# Cómo trata `roomrequest` los eventos únicos frente a los recurrentes

Relevamiento del módulo `roomrequest` (package `ar.edu.utn.frc.siga.roomrequest`)
sobre cuándo una solicitud de aula termina atada a un `UniqueEvent` y cuándo
a un `RecurringEvent`, y qué cambia en validación, armado del ítem y
asignación según cuál sea. Es una foto puntual, no reemplaza el código:
ante cualquier duda sobre una regla, `AbstractRoomRequestHandler` y sus
siete implementaciones en `roomrequest/handler/` son la fuente de verdad.

## Qué tipo de evento usa cada `RoomRequestType`

`RoomRequestType` tiene siete valores. Tres se resuelven contra un
`RecurringEvent` ya existente (el cursado de la comisión, sincronizado
desde SysAcad o cargado a mano); los otros cuatro crean un `UniqueEvent`
nuevo:

| `RoomRequestType` | Handler | DTO de ítem | Evento académico |
|---|---|---|---|
| `ONE_TIME_ROOM_CHANGE` | `OneTimeRoomChangeHandler` | `ScheduledItemDto` | `RecurringEvent` existente |
| `REGULAR_ROOM_CHANGE` | `RegularRoomChangeHandler` | `ScheduledItemDto` | `RecurringEvent` existente |
| `PARTIAL_EXAM_IN_CLASS` | `PartialExamInClassHandler` | `ScheduledItemDto` | `RecurringEvent` existente |
| `PARTIAL_EXAM_OFF_SCHEDULE` | `PartialExamOffScheduleHandler` | `FreeFormItemDto` | `UniqueEvent` nuevo, `kind=PARCIAL` |
| `FINAL_EXAM` | `FinalExamHandler` | `FreeFormItemDto` | `UniqueEvent` nuevo, `kind=EXAMEN_FINAL` |
| `CONFERENCE` | `ConferenceHandler` | `FreeFormItemDto` | `UniqueEvent` nuevo, `kind=OTRO` |
| `OTHER` | `OtherHandler` | `FreeFormItemDto` | `UniqueEvent` nuevo, `kind=OTRO` |

`RoomRequestHandlers` mapea cada `RoomRequestType` a su handler con un
`EnumMap` armado en el constructor y falla al arrancar si a algún tipo le
falta implementación, así que esta tabla no puede quedar desactualizada
sin romper el arranque de la aplicación.

## Armado del ítem: dos familias de handler

`AbstractRoomRequestHandler.assemble()` es el método template que arma el
`RoomRequest` y sus ítems para los siete tipos: crea la solicitud con los
datos del solicitante, y por cada ítem del DTO llama al `buildItem()`
abstracto que cada handler implementa distinto. La diferencia entre los
dos grupos de la tabla anterior está ahí, en cómo cada handler resuelve
fecha, horario y duración del ítem.

Los tres handlers de `ScheduledItemDto` (`OneTimeRoomChangeHandler`,
`RegularRoomChangeHandler`, `PartialExamInClassHandler`) no reciben
horario del frontend: lo derivan de `ClassScheduleService`, que consulta
los `RecurringEvent` de la comisión. `requireClassDate()` resuelve la
clase para una fecha puntual, `requireClassDay()` la resuelve para un día
de dictado, y ambas fallan con `InvalidRoomRequestException` si la
comisión no dicta clase ese día o si hay más de un horario posible para
esa comisión (caso que el trámite todavía no sabe desambiguar). El
`ClassSlot` que devuelven trae `recurringEventId`, que cada handler graba
en `RoomRequestItem.sourceRecurringEventId` solo para trazabilidad: de
qué evento recurrente salió el horario del pedido.

Los cuatro handlers de `FreeFormItemDto` (`PartialExamOffScheduleHandler`,
`FinalExamHandler`, `ConferenceHandler`, `OtherHandler`) no consultan
ningún evento existente: `AbstractRoomRequestHandler.freeFormItem()` arma
el ítem directo con la fecha, hora de inicio y duración que mandó el
frontend en el DTO. No hay clase de la que derivar el horario porque estos
cuatro tipos no pertenecen al cursado de una comisión.

## Validaciones que distinguen cada tipo

Las reglas concretas de `ItemConsistency` (`roomrequest/validator/`) que
cada handler invoca en `validateItems()`/`validateReferences()` marcan las
diferencias de negocio entre eventos únicos y recurrentes:

- **Cantidad de ítems.** `FinalExamHandler` es el único que llama
  `requireExactlyOne()`: un final se pide de a uno por solicitud. Los
  otros seis tipos, incluidos los tres de evento recurrente, aceptan
  varios ítems en la misma solicitud.
- **Aviso previo de 2 horas.** `requireExamAdvanceNotice()` corre en
  `FinalExamHandler`, `PartialExamOffScheduleHandler` y también en
  `PartialExamInClassHandler`: los tres son exámenes, aunque el último
  esté atado a un `RecurringEvent`. `ConferenceHandler` y `OtherHandler`
  solo exigen `requireNotPast()`, sin margen de anticipación, porque no
  son exámenes.
- **`requiresExamUsers`.** `requireExamUsersConsistent(true, ...)` corre
  en los tres tipos de examen (`FINAL_EXAM`, `PARTIAL_EXAM_OFF_SCHEDULE`,
  `PARTIAL_EXAM_IN_CLASS`); en el resto corre con `false`, así que el
  campo tiene que venir nulo salvo que el pedido pida computadoras.
- **Comisión obligatoria u opcional.** `FinalExamHandler`,
  `ConferenceHandler` y `OtherHandler` llaman `requireNoCommission()`: un
  final se pide por materia, una conferencia u "otro" no pertenecen al
  cursado de ninguna comisión. `PartialExamOffScheduleHandler` es la
  excepción dentro del grupo de evento único: la comisión es opcional por
  ítem, y si viene, `validateReferences()` valida que pertenezca a la
  materia con `academicReference.requireCommissionOfSubject()`.
- **Solapamiento contra otras solicitudes activas.** Solo
  `PartialExamOffScheduleHandler` valida esto, en dos capas.
  `ItemConsistency.requireNoCommissionOverlap()` rechaza ítems que se
  superponen entre sí dentro de la misma solicitud. Además,
  `requireNoOverlapWithExistingRequests()` en el propio handler consulta
  `RoomRequestItemRepository.findActiveOffScheduleItemsByDateIn()`, que
  trae los ítems `PARTIAL_EXAM_OFF_SCHEDULE` no cancelados de esas fechas,
  y usa `ItemConsistency.commissionScheduleOverlap()` para rechazar un
  nuevo pedido si otra solicitud ya tiene, para la misma comisión, fecha y
  franja horaria, un pedido activo. Ningún otro tipo de los siete corre
  esta validación cruzada contra otras solicitudes.

## El `UniqueEvent` no existe hasta el primer `assign`

La diferencia más importante para el flujo de aprobación no está en la
creación de la solicitud, sino en la asignación de aula.
`RoomRequestOccurrenceResolver.resolveTargetOccurrences()`, que
`RoomRequestResolutionServiceImpl.assign()` invoca en cada
`POST /assign`, resuelve distinto según el tipo:

- Para `ONE_TIME_ROOM_CHANGE` y `PARTIAL_EXAM_IN_CLASS`, busca la
  ocurrencia del `RecurringEvent` que cae en la fecha del ítem.
- Para `REGULAR_ROOM_CHANGE`, busca todas las ocurrencias futuras del
  `RecurringEvent` que caen en el día de la semana del ítem: un cambio
  regular aplica a cada clase que quede por dictarse desde hoy, no a una
  sola fecha.
- Para `PARTIAL_EXAM_OFF_SCHEDULE`, `FINAL_EXAM`, `CONFERENCE` y `OTHER`,
  si es la primera asignación (`reassigning == false`), llama
  `createEventOccurrence()`, que arma un `CreateUniqueEventRequestDto` con
  el `UniqueEventKind` correspondiente al tipo (mapeo en la tabla de
  arriba) y crea el `UniqueEvent` recién ahí, vía
  `AcademicEventService.createUniqueEvent()`. Si en cambio es una
  reasignación (`reassigning == true`), reutiliza la ocurrencia que ya
  tiene registrada en `RoomRequestItemAllocation` en vez de crear un
  segundo evento.

Esto quiere decir que una solicitud de estos cuatro tipos, mientras está
en `NEW` o `DERIVED_TO_BUILDING`, no tiene todavía ningún `AcademicEvent`
en la base: fecha, hora y duración viven solo en las columnas del
`RoomRequestItem`. El `UniqueEvent` se crea una única vez, en el primer
`assign` que le encuentra aula, y de ahí en más las reasignaciones lo
reutilizan. `UniqueEvent.toOccurrences()` siempre devuelve exactamente una
`Occurrence`, así que no hay ambigüedad sobre cuál ocurrencia le
corresponde al ítem una vez creado el evento.

## El preview de aulas candidatas no depende de que el `UniqueEvent` exista

`RoomRequestCandidateResolver.occupiedClassroomIds()`, que alimenta tanto
`GET /allowed-classrooms` como `GET /candidate-buildings`, calcula
ocupación consultando `AllocationOccupancyService.findOccupancy()` por
fecha y comparando franjas horarias directo contra `item.getDate()` y
`item.endTime()`. No pasa por el `AcademicEvent` ni por sus ocurrencias en
ningún momento. Por eso el preview funciona igual para los cuatro tipos de
evento único que para los de evento recurrente con fecha: la ocupación se
calcula contra la fecha y horario que ya tiene el `RoomRequestItem`, sin
importar si todavía no existe ningún `UniqueEvent` detrás.

La única salvedad no tiene que ver con eventos únicos sino con
`REGULAR_ROOM_CHANGE`: como sus ítems no tienen `date` (usan `dayOfWeek`),
`occupiedClassroomIds()` devuelve un set vacío para ellos y el preview
muestra todas las aulas candidatas como libres.

## Vencimiento automático: la regla genérica alcanza a los cuatro tipos

`RoomRequestExpiryServiceImpl.expireOverdueItems()` cancela automáticamente
los ítems activos cuya fecha ya pasó, vía
`RoomRequestItemRepository.findExpiredByDate()`, que filtra por `date <
:today` y excluye explícitamente `REGULAR_ROOM_CHANGE`. Los cuatro tipos
de evento único entran en esa regla genérica sin tratamiento especial,
porque `FreeFormItemDto` siempre trae `date`. `REGULAR_ROOM_CHANGE` es el
que necesita lógica aparte, en `expiredRegularRoomChangeItems()`, porque
sus ítems no tienen fecha propia: vencen cuando el `RecurringEvent` del
que salieron (vía `sourceRecurringEventId`) llega a su `endDate`.

## No hay un `sourceUniqueEventId`: la trazabilidad es indirecta

`RoomRequestItem.sourceRecurringEventId` graba de qué `RecurringEvent`
salió el horario del ítem, pero solo lo completan los tres handlers de
`ScheduledItemDto`. Los cuatro handlers de evento único nunca lo setean
porque `freeFormItem()` no toca esa columna, así que queda `null`. No
existe un campo equivalente para el `UniqueEvent`: el único vínculo entre
un `RoomRequestItem` y el evento que terminó creándole `assign()` pasa por
`RoomRequestItemAllocation.occurrenceId`, que hay que cruzar contra la
tabla `ocurrencia` para llegar al `id_evento_academico`. Ningún DTO de
respuesta del módulo (`RoomRequestItemResponseDto`,
`RoomRequestItemDetailDto`) expone ese id directamente; quien necesite
saber a qué `UniqueEvent` quedó atado un ítem tiene que resolverlo del
lado de `events`, no de `roomrequest`.
