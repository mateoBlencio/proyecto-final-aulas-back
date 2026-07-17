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

### Reglas de asignación (en `AllocationServiceImpl`)

- Todo `@Transactional`; `source` se estampa **adentro** del método (el HTTP no lo elige).
- `assignManually`: valida no-pasado, estado asignable, y que la ocurrencia no tenga ya aula.
- **Toda asignación manual valida no-solape contra ocupación `ASSIGNED`,
  vía el mismo helper privado `validateNoOverlap`**: `assignManually`, `reassign`,
  `batchReassign` y `assignManuallyFromDate`. Antes solo `assignManuallyFromDate` lo hacía;
  ahora es invariante único para las cuatro rutas.
  - `validateNoOverlap` recibe una lista de `OverlapCandidate` (ocurrencia + aula destino),
    filtra las que ya pasaron (no se validan), y hace **una sola** consulta de ocupación
    (`findOccupancyBetween`) sobre el rango `[min, max]` de fechas de los candidatos,
    excluyendo las ocurrencias propias de la operación.
  - Chequea dos frentes: `databaseConflicts` (candidato vs. ocupación firme de BD) e
    `internalConflicts` (candidatos entre sí — relevante para `batchReassign`, donde varios
    moves se validan **juntos antes de escribir ninguno**, incluidos los moves del lote
    cruzándose entre ellos).
  - Cualquier conflicto → `ReassignConflictException` (**409**) con el detalle completo
    (`List<OccurrenceConflictDto>`); nada se persiste.
  - Franjas horarias **adyacentes** (fin de una == inicio de la otra) **no** conflictúan
    (`overlaps` usa `<`/`<` estricto, no `<=`).
- `assignManuallyFromDate`: solo recurrentes; además de `validateNoOverlap`, hace **clamp**
  de `fromDate` a hoy si viene en el pasado (`effectiveFrom = max(fromDate, today)`).
- `importAssignmentsFromDate`: variante para excel (source `IMPORTED`, no saltea pasado,
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

**Estado actual: tres suites unitarias**, el resto del módulo sigue sin
cobertura:

- `AllocationServiceImplTest` — `assignManually`/`reassign`/`batchReassign`/
  `assignManuallyFromDate` felices y en conflicto (409), franjas adyacentes no
  conflictúan, `batchReassign` no persiste nada si un move choca (ni contra BD ni entre
  moves del lote).
- `AllocationProblemServiceImplTest` — sobrecupo (con y sin `enrolled` null),
  superposición (mismo/distinto aula, misma/distinta fecha, franjas adyacentes),
  agregación de fechas por par recurrente, rango por defecto (`to` = fin de período
  activo, fallback `from + 6 meses`), `InvalidDateRangeException`.
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
</content>
