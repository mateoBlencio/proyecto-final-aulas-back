# ADR-012: Desacople de `events` y `allocation` en dos módulos Modulith

## Estado

Aceptado — 2026-08-05

## Contexto

El módulo `allocation` mezclaba dos contextos bien distintos: el **evento
académico** (`AcademicEvent`/`RecurringEvent`/`UniqueEvent`/`Occurrence`, su alta, su
calendario, su horario y su auditoría) y la **asignación de aula** (`Allocation`,
validaciones de solapamiento/capacidad, importación masiva y orquestación del solver).

El acoplamiento era **bidireccional**, lo que impedía separarlos:

- `allocation → evento`: `Allocation.occurrence` era un `@OneToOne` JPA;
  `AllocationValidator`, `AllocationWriter` y `AutoAllocationDataLoader` navegaban
  `Occurrence → AcademicEvent` para leer `startTime`/`endTime`/`enrolled`;
  `AllocationRepository.findOccupancyBetween` hacía `JOIN FETCH o.event e`.
- `evento → allocation`: `AcademicEventServiceImpl.createUniqueEvent`/
  `updateUniqueEvent` inyectaban `AllocationService`/`AllocationRepository`;
  `AcademicEventComposer` resolvía el aula del evento único vía `AllocationRepository`.

Esto violaba la regla de [ADR-004](ADR-004-fronteras-de-modulo-servicios-y-dtos.md)
("comunicación entre módulos por ID + DTO, nunca compartiendo entidades JPA") de forma
silenciosa: como todo vivía en el mismo módulo Modulith, `ModularityTests
.verifyBoundaries()` no tenía nada que verificar — el acoplamiento estaba oculto detrás
de una única frontera declarada.

## Decisión

Se separan en dos `@ApplicationModule` con dependencia **unidireccional**
`allocation → events :: api`, comunicándose por ID + DTO. `events` queda como slice
vertical completo (controller propio, sin conocer aulas); `allocation` pasa a ser la
capa de orquestación de asignación sobre `events` + `space` + `solver`.

**`Occurrence` vive en `events`** (es el evento el que la genera; así hay una sola
relación JPA que cortar: `Allocation.occurrence`). El contrato que hace posible el
corte es `OccurrenceSlotDto{occurrenceId, eventId, date, startTime, endTime, status,
enrolled}` — todo lo que `allocation` necesita de un evento es la franja que ocupa;
`enrolled` viaja para que `findOvercrowded` no dispare una segunda consulta.
`OccurrenceService` (`events :: api`) reemplaza el acceso directo de `allocation` a
`OccurrenceRepository`.

Se ejecutó en cuatro fases, cada una compilando y con `./mvnw test` verde:

1. **F1 — reorganizar sin cambio de comportamiento.** Todo el material de evento pasa
   a un sub-paquete de `allocation` (`allocation.events.**`, todavía sin
   `package-info.java` propio: la dependencia bidireccional seguía siendo legal). Se
   extrae `EventScheduleValidator` desde `AllocationValidator` (`validateBusinessHours`
   era regla de horario del evento, no de asignación).
2. **F2 — cortar `Allocation → Occurrence` (la relación JPA).**
   `Allocation.occurrence` (`@OneToOne`) pasa a `occurrenceId` (`Long`, mismo patrón
   que `Allocation.classroomId` ya usaba para `space::Classroom`) — la FK física no
   cambia, sin DDL nuevo. Nacen `OccurrenceSlotDto` + `OccurrenceService`.
   `AllocationRepository` pierde los `@Query` con `JOIN FETCH o.event e`; el join pasa
   a ser en memoria por `occurrenceId`, dos queries acotadas por rango de fecha en vez
   de una sola con join — sin N+1 porque ambas son batch (`IN`). `AllocationWriter`
   cierra el pase a `ASSIGNED` con un único `OccurrenceService.markAssigned(ids)` por
   lote. **No se usa `@ApplicationModuleListener`**: el pase a `ASSIGNED` debe ser
   atómico con la escritura de la `Allocation`; un listener async (`REQUIRES_NEW` +
   `AFTER_COMMIT`) rompería esa invariante — la comunicación es llamada síncrona a la
   fachada, misma transacción.
3. **F3 — cortar `evento → allocation` (invertir la orquestación) — breaking para el
   front.** `AcademicEventServiceImpl` deja de inyectar `AllocationService`/
   `AllocationRepository`/`AllocationValidator`. Nace `UniqueEventAllocationService` en
   `allocation`: orquesta *crear evento único + asignar aula* dentro de una misma
   `@Transactional` (propagación `REQUIRED`, mismo datasource → la atomicidad se
   preserva). `UniqueEventResponseDto` (de `events`) pierde `status`/`classroom`/
   `overcrowdedBy`/`observation`; esos campos viven en el nuevo
   `UniqueEventAllocationResponseDto` de `allocation`, armado por
   `EventAllocationComposer`. Reubicación de endpoints (mismo nombre/semántica, cambia
   el prefijo): alta/modificación de evento único con aula y reasignación de aula de
   recurrente pasan de `/v1/events/**` a `/v1/allocations/events/**`; el historial de
   evento/ocurrencia pasa de `/v1/allocations/occurrences/**` a `/v1/events/**`.
4. **F4 — promover `events` a módulo Modulith real.** `allocation.events.**` →
   `ar.edu.utn.frc.siga.events.**`; nuevo `events/package-info.java`
   (`allowedDependencies = {"academic :: api", "common"}`); `@NamedInterface("api")`
   sobre el contrato completo (`AcademicEventService`, `OccurrenceService`,
   `EventAuditHistoryService`, los DTOs de evento/ocurrencia/historial, los tres
   enums); `allocation` y `excelimport` agregan `"events :: api"` a sus
   `allowedDependencies`. `ModularityTests.verifyBoundaries()` pasa sin excepciones.

Ver [events.md](../modulos/events.md) y [allocation.md](../modulos/allocation.md) para
el detalle de API/estructura interna de cada módulo resultante.

## Consecuencias

- `events` no depende de `space` ni de `allocation` — un evento académico no conoce
  aulas, ni directa ni transitivamente.
- El front pierde `classroomId` en `POST/PUT /v1/events/unique/**` (movidos a
  `/v1/allocations/events/unique/**`) y `status`/`classroom`/`overcrowdedBy` en
  `GET /v1/events/unique` (la vista con aula es
  `GET /v1/allocations/events/unique`) — cambio de contrato acordado y documentado en
  la fase F3, comunicado aparte al equipo de front.
- La auditoría Envers ([ADR-007](ADR-007-auditoria-envers-en-allocation.md)) ahora
  cruza dos módulos: `AcademicEvent`/`Occurrence` se auditan y consultan desde
  `events` (`EventAuditHistoryService`), `Allocation` desde `allocation`
  (`AllocationAuditHistoryService`). El DDL de las tablas `_aud` no cambió — la fila
  `id_ocurrencia` de `asignacion_aula_aud` sigue siendo una columna plana, ya no una
  relación auditada.
- El `JOIN FETCH` sustituido por dos queries batch no midió regresión de performance
  perceptible en los flujos existentes (`AutoAllocationFlowIntegrationTest`): ambas
  siguen acotadas por rango de fecha y por `IN`, no N+1.
- Deuda de test-code: los tests de `allocation` que necesitan fixtures de evento
  (`AllocationValidatorTest`) siguen construyendo entidades de `events` directamente
  (`EventTestData`, movido de `AllocationTestData`) — `ModularityTests` no vigila el
  código de test (solo el classpath de producción vía `ApplicationModules.of
  (SigaApplication.class)`), así que esto es válido pero deliberadamente no tan
  disciplinado como el código de producción.

## Alternativas consideradas

- **Dejar `Occurrence` en `allocation` y que `events` la referencie por ID**: se
  descartó porque invertía la dirección natural del dominio (es el evento el que
  genera sus ocurrencias al crearse/actualizarse — `RecurringEvent.toOccurrences()`) y
  hubiera dejado la relación bidireccional intacta, solo con los nombres cambiados.
- **`@ApplicationModuleListener` para el pase a `ASSIGNED`**: se descartó porque un
  listener asíncrono (`REQUIRES_NEW`/`AFTER_COMMIT`, el patrón estándar de eventos de
  dominio en Spring Modulith) rompe la invariante de que "asignar aula" y "marcar la
  ocurrencia como ocupada" son atómicos — si el listener falla o corre en otra
  transacción, puede quedar una `Allocation` sin su `Occurrence` marcada, o viceversa.
- **Una sola fase grande en vez de F1–F4**: se descartó por riesgo — cada fase deja
  `./mvnw test` verde y el contrato HTTP estable hasta F3 (documentado como el único
  punto de ruptura), lo que permite revisar/mergear incrementalmente en vez de un PR
  gigante con múltiples cambios de comportamiento entremezclados.
