# Módulo `allocation`

## Responsabilidad

Corazón del dominio. Modela **eventos académicos** (recurrentes/únicos), sus
**ocurrencias** (fechas concretas con máquina de estados) y las **asignaciones de aula**
(allocations). Orquesta la asignación manual, la asignación "desde una fecha", la
detección de problemas de asignación (sobrecupo/superposiciones) y el flujo completo de
**asignación automática** vía `solver`: preview con re-resolución, validación de
movimiento sobre el preview y confirmación atómica. Ver
[`../modelo-dominio.md`](../modelo-dominio.md).

## API pública (`::api`)

Servicios: `AcademicEventService`, `AllocationService` (ambos `@NamedInterface("api")`,
consumidos por `excelimport`), `AllocationProblemService` y `AutoAllocationService`
(ambos solo internos, sin `::api`).

### Endpoints

**Eventos** (`/v1/events`):

| Método | Path | Descripción |
|---|---|---|
| GET | `/v1/events` | Todos los eventos |
| GET | `/v1/events/{id}` | Evento por ID |
| GET | `/v1/events/{id}/occurrences` | Ocurrencias del evento |
| POST | `/v1/events/recurring` | Crea evento recurrente + genera ocurrencias |
| POST | `/v1/events/unique` | Crea evento único (1 ocurrencia) |
| GET | `/v1/events/{id}/history` | Historial de auditoría del evento (Envers, ver más abajo) |
| PUT | `/v1/events/{eventId}/classroom` | Reasignación permanente: cambia el aula de **todas las ocurrencias futuras** de un evento recurrente (las pasadas quedan intactas). **409** si el evento no es recurrente, si ya finalizó, o si alguna ocurrencia solapa |

**Asignaciones** (`/v1/allocations`, `AllocationController`):

| Método | Path | Descripción |
|---|---|---|
| GET | `/v1/allocations/unassigned?from&to` | Eventos sin aula en el rango (ver más abajo) |
| GET | `/v1/allocations/overcrowded?from&to` | Aulas con sobrecupo en el rango (ver más abajo) |
| GET | `/v1/allocations/overlaps?from&to` | Superposiciones de horario-aula en el rango (ver más abajo) |
| GET | `/v1/allocations?date` | Asignaciones de un día |
| GET | `/v1/allocations/{id}` | Por ID |
| POST | `/v1/allocations/occurrences/{occurrenceId}` | Asignación manual (falla si ya tiene aula, si ya pasó, o **409** si solapa con otra asignación) |
| PUT | `/v1/allocations/{id}` | Reasignar aula (**409** si solapa) |
| PUT | `/v1/allocations/batch` | Reasignación en lote, atómica (**409** si algún move solapa con BD o con otro move del propio lote) |
| POST | `/v1/allocations/from-date` | Asigna aula a todas las ocurrencias futuras de un recurrente desde `fromDate` (clamped a hoy si es pasada; **409** si solapa) |
| GET | `/v1/allocations/occurrences/{occurrenceId}/history` | Historial de auditoría de la ocurrencia (Envers, ver más abajo) |
| GET | `/v1/allocations/occurrences/{occurrenceId}/allocation-history` | Historial de auditoría de las asignaciones de la ocurrencia (Envers, ver más abajo) |

**Asignación automática** (`/v1/allocations/auto-preview`, `AutoAllocationController`,
propio de `AutoAllocationService`):

| Método | Path | Descripción |
|---|---|---|
| POST | `/v1/allocations/auto-preview` | Corre el solver sobre `eventIds` (dedup), devuelve `AutoPreviewResponseDto` (no persiste) |
| GET | `/v1/allocations/auto-preview/{previewId}` | Recupera y recompone una preview guardada (**410** si expiró) |
| POST | `/v1/allocations/auto-preview/{previewId}/validate-move` | Valida mover un evento a otra aula sobre el preview ajustado por el front |
| POST | `/v1/allocations/auto-preview/{previewId}/confirm` | Confirma atómicamente la propuesta final, persiste e invalida el preview |

## Estructura interna

Capas estándar + `mapper` con **composers** (`AllocationComposer`, `AcademicEventComposer`):
arman los DTOs trayendo datos de `space`/`academic` por `findByIds` (batch, sin N+1).

### Modelo

- **`AcademicEvent`** — abstracta, herencia `JOINED`, discriminador `tipo_evento`.
  Auditada con Envers. Subtipos:
  - **`RecurringEvent`** — `dayOfWeek`, `startDate`, `endDate`, `subjectId`, `commissionId`
    (IDs planos cross-módulo). `toOccurrences()` genera semanalmente desde `startDate`
    hasta `endDate` (o `startDate + 1 año` si es null).
  - **`UniqueEvent`** — `date`, `description`; genera 1 ocurrencia.
- **`Occurrence`** — fecha + `OccurrenceStatus` (`SCHEDULED`/`ASSIGNED`/`CANCELLED`/`SUSPENDED`).
  `isPast()` compara contra `LocalDateTime.now()`.
- **`Allocation`** — `classroomId` (ID plano a `space`), `source`
  (`MANUAL`/`AUTOMATIC`/`IMPORTED`), `createdAt`, `observation`. `@OneToOne` con `Occurrence`.

### Validaciones de negocio (`validator/AllocationValidator`)

Submódulo privado (fuera de todo `::api`) que **centraliza todas las reglas de negocio de
asignación**, compartidas entre el flujo manual (`AllocationServiceImpl`) y el automático
(`AutoAllocationServiceImpl`). Los services solo orquestan (cargan entidades, persisten,
componen DTOs) y delegan cada validación acá:

- `validateNoOverlap` — ningún candidato (`AllocationCandidate` = ocurrencia + aula
  destino) solapa con ocupación `ASSIGNED` firme de BD ni con otros candidatos del lote.
  Filtra las ocurrencias ya pasadas, hace **una sola** consulta de ocupación
  (`findOccupancyBetween`) sobre el rango `[min, max]` de fechas, excluyendo las
  ocurrencias propias de la operación. Dos frentes: `databaseConflicts` (vs. BD) e
  `internalConflicts` (candidatos entre sí — relevante para lotes, que se validan
  **juntos antes de escribir ninguno**; dos ocurrencias del mismo evento nunca
  conflictúan entre sí). Cualquier conflicto → `ReassignConflictException` (**409**) con
  `List<OccurrenceConflictDto>`; nada se persiste. Franjas **adyacentes** (fin == inicio)
  **no** conflictúan (`overlaps` usa `<`/`<` estricto).
- `validateClassroomsAvailable` — batch: toda aula referenciada existe y está
  `available = true` (vía fachada `ClassroomService` de `space`).
- `validateNotPast` / `isApplicable` — ocurrencia ya sucedida no se modifica.
- `validateAssignable` — ocurrencia `CANCELLED`/`SUSPENDED` no recibe aula.
- `validateEventNotFinished` — evento con todas sus ocurrencias pasadas no se reasigna.
- `validateNoDuplicateEventIds` / `validateAllocationsBelongToPreview` /
  `validateBelongsToPreview` / `moveDatabaseConflicts` / `movePreviewConflicts` — reglas
  del flujo automático (confirm y validate-move).

### Intent methods de asignación (en `AllocationServiceImpl`)

- Todo `@Transactional`; `source` se estampa **adentro** del método (el HTTP no lo elige).
- `allocateManually`: 1 ocurrencia; valida no-pasado, asignable, sin allocation previa,
  aula disponible y no-solape.
- `reallocate`: cambia el aula de una allocation existente (mismas validaciones).
- `batchReallocate`: lote atómico; todos los moves se resuelven y validan (contra BD y
  entre sí) antes de escribir nada.
- `reassignEvent`: **reasignación permanente** — cambia el aula de todas las ocurrencias
  con `date >= hoy` de un evento recurrente (las pasadas quedan intactas). Rechaza evento
  no recurrente o finalizado (`validateEventNotFinished`). Expuesto como
  `PUT /v1/events/{eventId}/classroom`.
- `allocateManuallyFromDate`: solo recurrentes; además de `validateNoOverlap`, hace
  **clamp** de `fromDate` a hoy si viene en el pasado (`effectiveFrom = max(fromDate, today)`).
- `importAllocationsFromDate`: variante para excel (source `IMPORTED`, no saltea pasado,
  **sin** `validateNoOverlap` — la carga masiva no se bloquea por solapamientos).
- `allocateToOccurrences`: upsert (reusa allocation existente o crea), setea estado `ASSIGNED`.

### Historial de auditoría (`AuditHistoryServiceImpl`)

Consulta de solo lectura sobre las tablas `_aud` de Envers (ADR-007) vía `AuditReader`
(`AuditReaderFactory.get(entityManager)`), tres métodos `@Transactional(readOnly=true)`:

- **`findEventHistory(eventId)`** — `forRevisionsOfEntity(AcademicEvent.class, ...)`;
  con herencia JOINED la query devuelve el subtipo real, mapeado al snapshot polimórfico
  por `type` (`EventHistorySnapshotDto` sealed, mismo patrón que `AcademicEventResponseDto`).
- **`findOccurrenceHistory(occurrenceId)`** — revisiones de la ocurrencia (cambios de estado).
- **`findAllocationHistory(occurrenceId)`** — filtra por la FK auditada con
  `AuditEntity.relatedId("occurrence")` sin cargar la ocurrencia; por ocurrencia y no por
  `allocationId` porque la allocation puede borrarse/recrearse.

Contrato común `RevisionDto<T>{revision, date, user, kind, snapshot}` (orden ascendente);
`kind` mapea `RevisionType` ADD/MOD/DEL → CREATED/MODIFIED/DELETED; en DELETED el snapshot
va en null (Envers devuelve la entidad solo con el id). Historial vacío + entidad
inexistente hoy ⇒ **404** (si la entidad existe, el INSERT siempre auditó). Snapshots con
IDs planos sin componer contra otros módulos (el dato histórico crudo; el front resuelve
nombres). Ver [`../para-front.md`](../para-front.md).

### Escritura única (`service/impl/AllocationWriter`)

Componente package-private (sin interfaz) que es el **único punto de escritura de
asignaciones**: upsert por ocurrencia (reusa la allocation existente o crea una nueva) +
pase a `ASSIGNED`, con prefetch batch de las allocations existentes (sin N+1). Saltea
no-asignables (`CANCELLED`/`SUSPENDED`, o pasadas cuando `skipPast`) por diseño — no es
fallo parcial. Todos los flujos (manual, importado y el `confirm` automático) pasan por
acá; el intent method que llama decide las validaciones previas y estampa su `source`.
Corre dentro de la transacción del caller: las entidades managed se persisten por dirty
checking, solo las allocations nuevas requieren `save()`. Gancho futuro: cuando exista la
notificación al docente, este será el único lugar que publique el evento de dominio.

### Problemas de asignación (`AllocationProblemServiceImpl`)

Tres endpoints `@Transactional(readOnly=true)`, uno por tipo de problema, que comparten
la misma resolución de rango:

- **Rango por defecto** (los tres): `from` nulo ⇒ hoy; `to` nulo ⇒ el mayor `endDate`
  entre los períodos académicos activos (`AcademicPeriodService.findActive()`), o
  `from + 6 meses` si no hay período activo con `endDate`. `to < from` (efectivo) ⇒
  `InvalidDateRangeException` (**400**).
- **`GET /v1/allocations/unassigned`** — eventos con ocurrencias `SCHEDULED` (sin aula);
  delega en `AcademicEventService.findUnassignedEvents` con el rango ya resuelto.
- **`GET /v1/allocations/overcrowded`** — sobre una lectura de la ocupación `ASSIGNED`
  (`findOccupancyBetween`), agrupa por (evento, aula) y compara `enrolled` (`null` ⇒ `0`)
  contra `classroom.capacity()`; agrupa todas las fechas en conflicto de un mismo par
  evento-aula en una sola fila (`OvercrowdedAllocationDto`).
- **`GET /v1/allocations/overlaps`** — sobre la misma lectura de ocupación, agrupa por
  (aula, fecha), ordena por hora de inicio y barre con corte temprano (mismo patrón que
  `SolverServiceImpl.computeConflicts`) para evitar el producto cartesiano; los pares en
  conflicto se agregan por (eventoA, eventoB, aula) acumulando todas las fechas en que
  chocan (`ClassroomOverlapDto`). Franjas adyacentes no cuentan (criterio `<`/`<`).
- En `overcrowded`/`overlaps`, eventos y aulas ajenos se resuelven en **un solo batch cada
  uno** (`findByIds`) — sin N+1. Cada endpoint hace su propia lectura de ocupación
  (independientes; si el front pide los dos, son dos lecturas).

### Asignación automática (`AutoAllocationServiceImpl`)

Flujo completo de tres pasos sobre un `SolverPreview` cacheado por `solver`
(`PreviewStore`, TTL configurable):

1. **`autoPreview` — preview con re-resolución**. `eventIds` del request se
   deduplican vía `Set`. **Sin `@Transactional`**: la carga de datos vive en una
   transacción corta propia (`AutoAllocationDataLoader`, deuda B3 resuelta); el solve
   (hasta varios minutos) y la composición final corren **sin conexión JDBC retenida**.
   - Solo eventos recurrentes: si algún id resuelve a un `UniqueEvent`,
     `AllocationConflictException` (**409**).
   - Toma occurrences `SCHEDULED` **o** `ASSIGNED` con `date >= hoy` de los eventos
     seleccionados (incluir `ASSIGNED` permite re-resolver eventos ya asignados con
     sobrecupo/superposición).
   - La ocupación pinned que se le pasa al solver **excluye** las allocations de los
     propios eventos seleccionados (sus aulas quedan libres para que el solver las
     reasigne); el resto de la ocupación firme de BD sigue pinned.
   - Respuesta propia `AutoPreviewResponseDto{previewId, allocations, unresolved}`:
     `allocations` son las filas con aula propuesta, `unresolved` son los eventos que el
     solver no pudo ubicar sin conflicto (`classroomId == null`, revisión manual). Cada
     fila (`ProposedAllocationDto`) trae `overcrowdedBy`: alumnos que exceden la capacidad
     del aula propuesta (0 si entran; el front pinta alerta cuando es > 0).
   - **Floor de no-regresión**: un evento que el solver deja sin aula pero que **ya tenía
     una asignada** conserva esa aula previa (`priorRoomByEvent`) y queda en `allocations`,
     no en `unresolved`. Sólo los eventos nuevos (sin aula previa) caen en `unresolved`.
2. **`validateMove` — validación de movimiento sobre el preview**. Responde **200
   siempre** que el request sea coherente con el preview — el conflicto es un resultado
   esperado de la interacción de arrastre, no un error — con `valid=false` +
   `List<MoveConflictDto>` en el body (nunca **409** por conflicto de horario).
   Cada conflicto trae `origin` `DATABASE` (choca contra ocupación firme ajena al preview)
   o `PREVIEW` (choca contra otro ítem de `currentAllocations`, el estado ajustado por el
   front que viaja completo en cada request porque el backend solo cachea la corrida
   original del solver). **410** si el preview expiró; **409** si `eventId` o algún
   elemento de `currentAllocations` no pertenece al preview.
3. **`confirm` — confirmación atómica**. Persiste la propuesta **final**
   ajustada por el usuario. Todas las validaciones corren **antes de la primera
   escritura**:
   - Sin eventIds duplicados en `request.allocations()` (**409**).
   - Todo eventId de la propuesta pertenece al preview (**409** si hay ajenos).
   - Aulas de la propuesta existen y están `available` (**409** si alguna no).
   - Re-validación total contra BD (`databaseOverlapConflicts`, excluyendo la ocupación de
     los propios eventos del set) y del set entre sí (`internalOverlapConflicts`) →
     `ReassignConflictException` (**409**) con el detalle si hay algo, **sin persistir
     nada**.
   - `classroomId == null` para un evento ⇒ va a `skippedEventIds`, no se aplica.
   - Aplicar: actualiza la `Allocation` existente de cada ocurrencia (aula nueva +
     `source = AUTOMATIC`) o crea una si no había — **sin duplicar**; una ocurrencia que
     ya estaba `ASSIGNED` conserva ese estado, solo cambia de aula.
   - `source = AUTOMATIC` se estampa **siempre dentro del servicio**, nunca lo decide el
     cliente.
   - Invalida el preview al final (`solverService.invalidatePreview`): un **re-confirm**
     del mismo `previewId` da **410** (protección natural contra doble submit).

Los tres métodos reusan `AutoAllocationDataLoader.load(eventIds)`: carga eventos
recurrentes, fechas por evento, aulas disponibles y ocupación de BD (excluyendo los
eventos seleccionados) en una única transacción corta, materializando entidades
(`Hibernate.unproxy`) para que el resto del flujo pueda correr fuera de transacción.

## Dependencias

`space::api`, `academic::api`, `solver::api`, `common`. Es el módulo con más
dependencias (agrega el dominio).

## Gaps y oportunidades

- **`auto-preview` solo eventos recurrentes.** Los `UniqueEvent` no entran (lanza 409).
- **Sin transiciones de estado por API.** El enum tiene `CANCELLED`/`SUSPENDED` pero no hay
  endpoint para cancelar/suspender una ocurrencia ni para borrar una asignación.
- **`RecurringEvent.excludedDates` muerto.** Nunca se escribe (marcado obsoleto en el
  código); `toOccurrences` lo lee siempre vacío. Candidato a eliminar (entidad + tabla).
- **`isPast()` acoplado a `LocalDateTime.now()`.** No hay `Clock` inyectable → la lógica
  temporal (núcleo de las validaciones) es difícil de testear de forma determinista.

## Testing

Módulo con cobertura unitaria e integración (Testcontainers):

**Unitarias:**

- `model/` — `RecurringEventTest` (`toOccurrences()`: primer día, paso semanal, `endDate`
  null ⇒ `+1 año`, fechas excluidas), `UniqueEventTest`, `OccurrenceTest`
  (`isPast()`/`startTime()`/`endTime()`).
- `AllocationServiceImplTest` — `allocateManually`/`reallocate`/`batchReallocate`/
  `allocateManuallyFromDate` felices y en conflicto (409), `batchReallocate` no persiste
  nada si un move choca, `reassignEvent` (feliz solo-futuras, evento finalizado, evento
  único, inexistente).
- `AllocationValidatorTest` — todas las reglas del validator: solape BD/interno, franjas
  adyacentes, aulas disponibles, no-pasado, asignable, `validateEventNotFinished`,
  pertenencia al preview, conflictos de move.
- `AcademicEventServiceImplTest`, `AllocationProblemServiceImplTest` — sobrecupo (con y
  sin `enrolled` null), superposición, agregación de fechas por par, rango por defecto,
  `InvalidDateRangeException`.
- `AutoAllocationServiceImplTest` — dedup de `eventIds`, exclusión de la ocupación pinned
  de los eventos seleccionados, inclusión de occurrences `ASSIGNED` futuras
  (re-resolución), separación `allocations`/`unresolved`, 409 por `UniqueEvent`,
  `validateMove` (libre, conflicto BD, conflicto PREVIEW, ocupación propia liberada, 410,
  409, franjas adyacentes), `confirm` (alta y update sin duplicar, conserva `ASSIGNED`,
  invalida preview, 410 en re-confirm, 409 por duplicados/ajeno al preview/aula
  inexistente o no disponible/conflicto BD/conflicto interno, `skippedEventIds`).

- `AuditHistoryApiIntegrationTest` (Testcontainers, commits reales) — los 3 endpoints de
  historial: asignar → reasignar → borrar refleja CREATED/MODIFIED/DELETED (snapshot null
  en DELETED), transición SCHEDULED→ASSIGNED de la ocurrencia, alta del evento con campos
  del subtipo, usuario capturado, orden ascendente, lista vacía y 404.

Sin cobertura: `RecurringEvent.toOccurrences()`, `Occurrence.isPast()`, composers,
`AcademicEventServiceImpl`, y toda la capa de integración (Testcontainers).

### Unitarios recomendados (pendientes, alta prioridad)
- `RecurringEvent.toOccurrences()`: primer día = `nextOrSame(dayOfWeek)`, paso semanal,
  `endDate` null ⇒ `+1 año`, exclusión de fechas.
- `Occurrence.isPast()` / `startTime()` / `endTime()` (inyectar `Clock` primero).
- `AcademicEvent.endTime()` = `startTime + duration`.
- `allocateToOccurrences`: upsert (reusa vs crea), salteo de pasadas (`skipPast`), salteo
  de `CANCELLED`/`SUSPENDED`.
- Composers: armado de DTO sin N+1 (mockear `findByIds`).

### Integración (Testcontainers) recomendados
- Flujo completo `POST recurring` → ocurrencias generadas en BD.
- `assignManually` feliz + 409 por ocurrencia ya asignada + 409 por pasada.
- `from-date` con conflicto real → 409 con `OccurrenceConflictDto` correcto.
- `batchReassign` atómico: si una falla, ninguna se persiste.
- `confirm` atómico end-to-end: si una validación falla, nada persiste en BD.
- **Auditoría Envers**: verificar filas en `*_aud` tras crear/modificar.
  de los eventos seleccionados, re-resolución de `ASSIGNED` futuras, separación
  `allocations`/`unresolved`, 409 por `UniqueEvent`, `validateMove` completo, `confirm`
  completo (atómico, 410 en re-confirm, `skippedEventIds`).

**Integración (Testcontainers):** `AcademicEventApiIntegrationTest`,
`AllocationApiIntegrationTest` (incluye `reassignEvent` end-to-end: reasigna solo
futuras), `AllocationProblemsIntegrationTest`, `AutoAllocationFlowIntegrationTest`
(flujo preview → validate-move → confirm contra BD real).

Pendiente: composers (armado de DTO sin N+1), auditoría Envers (filas en `*_aud`),
`Clock` inyectable para testear `isPast()` determinista.
</content>
