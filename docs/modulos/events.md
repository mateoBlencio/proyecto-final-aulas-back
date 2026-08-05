# Módulo `events`

## Responsabilidad

Modela los **eventos académicos** (recurrentes/únicos) y sus **ocurrencias** (fechas
concretas con máquina de estados). Alta, consulta, calendario, horario y auditoría del
evento. **No conoce aulas**: no sabe qué es una asignación, no depende de `allocation`
ni de `space`. La asignación de aula a un evento único (atómica: crear evento + asignar
aula en la misma transacción) y la reasignación de aula de un evento recurrente viven en
`allocation` (`UniqueEventAllocationService`, `AllocationService#reassignEvent`), que
orquesta sobre este módulo por ID + DTO. Ver [`../modelo-dominio.md`](../modelo-dominio.md).

Nació por desacople de `allocation` (antes mezclaba evento + asignación en un solo
módulo, con dependencia bidireccional); ver [ADR-012](../adr/ADR-012-desacople-events-allocation.md).

## API pública (`::api`)

Servicios: `AcademicEventService`, `OccurrenceService`, `EventAuditHistoryService`
(los tres `@NamedInterface("api")`, consumidos por `allocation` y, `AcademicEventService`,
también por `excelimport` vía `findOrCreateRecurringEvent`).

DTOs `::api`: `AcademicEventResponseDto` (sealed: `RecurringEventResponseDto`/
`UniqueEventResponseDto`), `OccurrenceResponseDto`, `OccurrenceSlotDto` (la franja que
`allocation` necesita para validar solapamiento/capacidad, sin la entidad `Occurrence`),
`CreateRecurringEventRequestDto`, `CreateUniqueEventRequestDto`,
`UpdateUniqueEventRequestDto`, `EventHistorySnapshotDto` (sealed, ídem) y
`OccurrenceHistorySnapshotDto`. Enums `::api`: `EventType`, `OccurrenceStatus`,
`UniqueEventKind`.

### Endpoints (`/v1/events`, `AcademicEventController`)

| Método | Path | Descripción |
|---|---|---|
| GET | `/v1/events` | Todos los eventos (únicos sin aula/estado/sobrecupo — esos campos los agrega `allocation`) |
| GET | `/v1/events/{id}` | Evento por ID |
| GET | `/v1/events/{id}/occurrences` | Ocurrencias del evento |
| GET | `/v1/events/unique` | Solo eventos únicos, sin aula. Ver `GET /v1/allocations/events/unique` para la vista con aula |
| POST | `/v1/events/recurring` | Crea evento recurrente + genera todas sus ocurrencias |
| POST | `/v1/events/unique/{id}/cancel` | Baja lógica: cancela la ocurrencia (no borra), libera el aula para nuevas asignaciones |
| GET | `/v1/events/{id}/history` | Historial de auditoría del evento (Envers, ver más abajo) |
| GET | `/v1/events/occurrences/{occurrenceId}/history` | Historial de auditoría de la ocurrencia (Envers) |

El alta/modificación de un evento único **con aula**, y la reasignación de aula de un
evento recurrente, viven en `allocation` (`POST/PUT /v1/allocations/events/unique/**`,
`PUT /v1/allocations/events/{id}/classroom`) — necesitan `classroomId` y atomicidad
evento+asignación.

## Estructura interna

### Modelo

- **`AcademicEvent`** — abstracta, herencia `JOINED`, discriminador `tipo_evento`.
  Auditada con Envers. `subjectId`/`commissionId` (IDs planos cross-módulo hacia
  `academic`, ambos nullable) viven acá, compartidos por los dos subtipos. Subtipos:
  - **`RecurringEvent`** — `dayOfWeek`, `startDate`, `endDate`. `toOccurrences()` genera
    semanalmente desde `startDate` hasta `endDate` (o `startDate + 1 año` si es null).
  - **`UniqueEvent`** — `date`, `kind` (`UniqueEventKind`: `PARCIAL`/`TRABAJO_PRACTICO`/
    `EXAMEN_FINAL`/`OTRO`), `description`; genera 1 ocurrencia. `subjectId` obligatorio
    salvo `kind=OTRO`; `commissionId` nunca obligatorio por sí solo, pero no puede venir
    sin `subjectId` (validado en el service, ver ADR-011).
- **`Occurrence`** — fecha + `OccurrenceStatus` (`SCHEDULED`/`ASSIGNED`/`CANCELLED`/
  `SUSPENDED`). `isPast()` compara contra `LocalDateTime.now()`. El pase a `ASSIGNED` lo
  dispara `allocation` vía `OccurrenceService#markAssigned` (misma transacción del
  caller); no hay `@ApplicationModuleListener` — la atomicidad "asignar aula ⇒ marcar
  ocupada" tiene que ser síncrona.

`EventScheduleValidator` centraliza las reglas de horario/referencia académica del
evento (`validateBusinessHours` contra `EventScheduleProperties`/`siga.events.hours`,
`validateAcademicReference`, `validateCommissionBelongsToSubject`, `validateNotPast`).

### `AcademicEventComposer`

Arma los DTOs de evento resolviendo `subject`/`commission` contra `academic` por
`findByIds` (batch, sin N+1). Ya no resuelve aula/estado de asignación — eso lo hace
`EventAllocationComposer` en `allocation`.

### `OccurrenceService`

Contrato de solo lectura (más el pase a `ASSIGNED`) que expone la franja de las
occurrences sin que el consumidor toque la entidad `Occurrence`: `findSlot(s)`,
`findSlotsByEvent(s)`, `findSlotsByEventsAndStatuses`, `findSlotsByStatusBetween`,
`findSlotsByDate`, `existsOccurrence`, `markAssigned`. Es el contrato completo que
`allocation` necesita para validar solapamiento/capacidad y cerrar el ciclo de
asignación, sin relación JPA cross-módulo.

### Historial de auditoría (`EventAuditHistoryServiceImpl`)

Consulta de solo lectura sobre las tablas `_aud` de Envers (ver [ADR-007](../adr/ADR-007-auditoria-envers-en-allocation.md))
vía `AuditReader`, dos métodos `@Transactional(readOnly=true)`:

- **`findEventHistory(eventId)`** — `forRevisionsOfEntity(AcademicEvent.class, ...)`;
  con herencia JOINED la query devuelve el subtipo real, mapeado al snapshot polimórfico
  por `type` (`EventHistorySnapshotDto` sealed, mismo patrón que `AcademicEventResponseDto`).
- **`findOccurrenceHistory(occurrenceId)`** — revisiones de la ocurrencia (cambios de estado).

Contrato común `RevisionDto<T>{revision, date, user, kind, snapshot}` (orden ascendente);
`kind` mapea `RevisionType` ADD/MOD/DEL → CREATED/MODIFIED/DELETED; en DELETED el
snapshot va en null. Historial vacío + entidad inexistente hoy ⇒ **404**. Snapshots con
IDs planos sin componer contra otros módulos (dato histórico crudo; el front resuelve
nombres). El historial de las asignaciones en sí (`AllocationAuditHistoryService`) vive
en `allocation`.

## Dependencias

`academic :: api`, `common`. No depende de `space` ni de `allocation` — es
intencional: un evento no conoce aulas.

## Gaps y oportunidades

- **`RecurringEvent.excludedDates` muerto.** Nunca se escribe (marcado obsoleto en el
  código); `toOccurrences` lo lee siempre vacío. Candidato a eliminar (entidad + tabla).
- **`isPast()` acoplado a `LocalDateTime.now()`.** No hay `Clock` inyectable → la lógica
  temporal es difícil de testear de forma determinista.
- **Sin transiciones de estado por API propias.** El enum tiene `CANCELLED`/`SUSPENDED`
  pero solo `cancelUniqueEvent` las dispara; no hay endpoint para suspender una ocurrencia.

## Testing

**Unitarias:**

- `model/` — `RecurringEventTest` (`toOccurrences()`: primer día, paso semanal,
  `endDate` null ⇒ `+1 año`, fechas excluidas), `UniqueEventTest`, `OccurrenceTest`
  (`isPast()`/`startTime()`/`endTime()`).
- `validator/EventScheduleValidatorTest` — horario dentro/fuera de rango, referencia
  académica faltante, comisión que no pertenece a la materia.
- `service/impl/AcademicEventServiceImplTest` — alta recurrente + generación de
  ocurrencias, `findOrCreateRecurringEvent` (reutiliza vs. crea), alta/modificación/
  cancelación de evento único, listado de eventos sin aula por rango de fechas.

**Integración (Testcontainers):** `AcademicEventApiIntegrationTest`,
`AuditHistoryApiIntegrationTest` (historial de evento y ocurrencia; el de asignación
vive en `allocation`).

Sin cobertura: `RecurringEvent.toOccurrences()` / `Occurrence.isPast()` de forma
aislada (cubiertas indirectamente vía los tests de modelo), `AcademicEventComposer`.

### Recomendados (pendientes)
- `Clock` inyectable para `isPast()` determinista.
- `AcademicEventComposer`: armado de DTO sin N+1 (mockear `findByIds`).
