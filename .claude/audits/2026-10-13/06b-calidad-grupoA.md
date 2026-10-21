# 06b Calidad de código, grupo A (allocation, roomrequest, optimizer, preview, events)

Base: commit 22c693f2. Solo lectura, sin mvnw. Excluido sysacad. Los comentarios `ponytail:` no existen en estos cinco módulos (grep vacío), así que ponytail-debt no aporta ítems. No hay identificadores con "assignation" ni identificadores fuera de inglés (los literales "asignacion_aula", "asignación" aparecen solo en nombres de tabla y mensajes en español).

### QA-A-01 El solver corre en PHASE_ASSERT en todos los perfiles
- Severidad: Media
- Esfuerzo: S (<1h)
- Referencia: ISO 25010 Performance efficiency/Resource utilization
- Evidencia: src/main/resources/application.yaml:165 (`environment-mode: PHASE_ASSERT`), src/main/java/ar/edu/utn/frc/siga/optimizer/config/OptimizerProperties.java:15; ningún application-*.yaml lo pisa (grep en src/main/resources).
- Verificación: Confirmado
- Problema: PHASE_ASSERT es un modo de depuración de Timefold que recalcula el score completo tras cada fase y verifica corrupción de variables. Producción lo hereda, así que cada auto-preview y cada sugerencia gastan tiempo de su límite en verificaciones.
- Arreglo: poner `environment-mode: NON_REPRODUCIBLE` en application-prod.yaml (y dejar PHASE_ASSERT en test/dev-local).

### QA-A-02 findConflicts lee la ocupación tres veces
- Severidad: Media
- Esfuerzo: S (<1h)
- Referencia: ISO 25010 Performance efficiency/Time behaviour
- Evidencia: src/main/java/ar/edu/utn/frc/siga/allocation/service/impl/AllocationConflictServiceImpl.java:118-154 (buildOvercrowdedConflicts, buildOverlapConflicts, buildNotPermittedConflicts llaman cada uno a readOccupancy) y :209-213.
- Verificación: Confirmado
- Problema: con `types` vacío (default del endpoint) se ejecutan los tres, y cada uno hace `findSlotsBetween` + `findByOccurrenceIdIn` sobre el mismo rango (hasta fin del período activo, la base de dev tiene 74.698 ocurrencias de marzo a noviembre). Son tres lecturas idénticas por request.
- Arreglo: leer `readOccupancy(range, includePast)` una vez en `findConflicts` y pasarlo a los tres builders.

### QA-A-03 loadOccupancy trae todas las ocurrencias del rango por cada validación de solapamiento
- Severidad: Media
- Esfuerzo: M (<1 día)
- Referencia: ISO 25010 Performance efficiency/Resource utilization
- Evidencia: src/main/java/ar/edu/utn/frc/siga/allocation/validator/AllocationValidator.java:76-90; también AllocationOccupancyReader.java:21-29.
- Verificación: Plausible
- Problema: para validar un lote, `loadOccupancy` carga todas las ocurrencias (con su evento) entre la fecha mínima y máxima del lote y después filtra las que tienen asignación. Un reallocate de un evento anual recorre unas 60 mil filas. No medí el tiempo real.
- Arreglo: consulta directa de asignaciones por `id_aula IN (aulas del lote)` y rango de fechas, en vez de partir de todas las ocurrencias.

### QA-A-04 Confirmar una sugerencia de preview que falla quema la sugerencia
- Severidad: Baja
- Esfuerzo: S (<1h)
- Referencia: ISO 25010 Reliability/Fault tolerance
- Evidencia: src/main/java/ar/edu/utn/frc/siga/preview/service/impl/PreviewServiceImpl.java:184-191 frente a src/main/java/ar/edu/utn/frc/siga/roomrequest/service/impl/DirectEventServiceImpl.java:188-193.
- Verificación: Confirmado
- Problema: `takeSuggestion` elimina la sugerencia del cache antes de `reallocate`. Si `reallocate` lanza 409 (solapamiento nuevo), el rollback de la transacción no restaura el cache y el reintento recibe `ExpiredPreviewException`. DirectEventServiceImpl ya resuelve el mismo caso con `store.save(suggestion)` en el catch.
- Arreglo: envolver `reallocate` en try/catch que re-guarde la sugerencia, igual que DirectEventServiceImpl.

### QA-A-05 @Transactional(readOnly) de PreviewEngine.loadInputs se ignora al llamarlo desde generate()
- Severidad: Baja
- Esfuerzo: S (<1h)
- Referencia: ISO 25010 Maintainability/Analysability
- Evidencia: src/main/java/ar/edu/utn/frc/siga/preview/service/impl/PreviewEngine.java:61 y :90.
- Verificación: Confirmado
- Problema: `generate()` invoca `loadInputs` en la misma clase (self-invocation), por lo que no pasa por el proxy y la anotación no aplica. Solo funciona cuando PreviewServiceImpl llama a `loadInputs` desde afuera (composeForResponse, confirm). El código sugiere una lectura consistente que en autoPreview no existe.
- Arreglo: dejar un comentario que lo aclare o mover `generate` a PreviewServiceImpl para que llame `previewEngine.loadInputs` por el proxy.

### QA-A-06 confirm() de preview repite el chequeo de solapamiento que reallocate ya hace
- Severidad: Baja
- Esfuerzo: S (<1h)
- Referencia: ISO 25010 Maintainability/Modularity (tag: shrink)
- Evidencia: src/main/java/ar/edu/utn/frc/siga/preview/service/impl/PreviewServiceImpl.java:116-134 frente a AllocationServiceImpl.java:175-184 (`validateStrictOverlap`).
- Verificación: Plausible
- Problema: `confirm` carga `loadInputs` completo (aulas, permisos, eventos) solo para usar `databaseOccupancy()` en `validateNoOverlap`, y luego `reallocate(AllocationCommand.automatic)` valida el mismo solapamiento estricto con lock por aula. Creo que son equivalentes, pero no comparé tests que cubran la diferencia.
- Arreglo: borrar `validateNoOverlap`, `targetOccurrences` y `candidates` de confirm y dejar que falle `reallocate`.

### QA-A-07 DirectEventServiceImpl parsea el índice del evento desde el planningId
- Severidad: Baja
- Esfuerzo: S (<1h)
- Referencia: ISO 25010 Maintainability/Modifiability
- Evidencia: src/main/java/ar/edu/utn/frc/siga/roomrequest/service/impl/DirectEventServiceImpl.java:99-100, :112-116, :160.
- Verificación: Confirmado
- Problema: el orden de las aulas sugeridas se recupera con `Integer.parseInt(eventId.substring(lastIndexOf('-')+1))`, acoplado al formato `"de-<hash>-<i>"`. Además la sugerencia se ata al borrador por `draft.hashCode()` (32 bits), así que dos borradores distintos con hash igual comparten sugerencia.
- Arreglo: mapear `planningId -> i` en un `Map<String,Integer>` armado al crear los eventos, y guardar en la sugerencia un hash más fuerte o el borrador mismo.

### QA-A-08 AcademicEventServiceImpl duplica código y mezcla tres responsabilidades
- Severidad: Baja
- Esfuerzo: M (<1 día)
- Referencia: ISO 25010 Maintainability/Modularity (tag: shrink)
- Evidencia: src/main/java/ar/edu/utn/frc/siga/events/service/impl/AcademicEventServiceImpl.java:162-176 duplica el builder de :214; tres sobrecargas `keyOf` (:340-355); `reconcileDuration` (:399) devuelve boolean que nadie lee; :431 `recurringEventRepository.save(event)` sobre entidad ya gestionada; el archivo tiene 563 líneas (CRUD, sync SysAcad, evento único).
- Verificación: Confirmado
- Problema: `createRecurringEvent` arma el evento a mano en vez de usar `buildRecurringEvent(dto)`. Hay código muerto (retorno ignorado, save redundante).
- Arreglo: usar `buildRecurringEvent(dto)` en createRecurringEvent, volver `reconcileDuration` void y quitar el save. Partir la clase queda como deuda.

### QA-A-09 Código muerto y duplicado en roomrequest
- Severidad: Baja
- Esfuerzo: S (<1h)
- Referencia: ISO 25010 Maintainability/Reusability (tag: delete)
- Evidencia: roomrequest/model/RoomRequestStatus.java:37 (`isCancelled()` sin usos en src/main ni src/test); roomrequest/direct/DirectEventDraftValidator.java:105-117 repite la lógica de ItemConsistency.java:65-75 (`requireExamUsersConsistent`); roomrequest/model/RoomRequestItem.java:224-225 tiene dos Javadoc apilados (el primero se descarta) y usa `java.util.stream.Collectors` inline en :228.
- Verificación: Confirmado
- Problema: tres restos que confunden la lectura: método sin uso, regla duplicada con mensajes distintos, Javadoc huérfano.
- Arreglo: borrar `isCancelled`, hacer que el validator delegue en una versión de ItemConsistency que reciba los campos sueltos, unir los dos Javadoc e importar Collectors.

### QA-A-10 Constructores públicos vacíos en las siete restricciones del optimizer
- Severidad: Baja
- Esfuerzo: S (<1h)
- Referencia: ISO 25010 Maintainability/Analysability (tag: delete)
- Evidencia: src/main/java/ar/edu/utn/frc/siga/optimizer/constraint/{NoOverlap,PermissionNotSatisfied,PreferSameRoomSameCommission,PreferSameBuildingSameCommission,MinimizeOvercrowding,MinimizeUnusedCapacity,AllocateAllPossible}.java
- Verificación: Confirmado
- Problema: cada clase declara `public X() {}`, idéntico al constructor por defecto.
- Arreglo: borrarlos. Deuda menor: AllocationSettings/OptimizerSettings/PreviewSettings son interfaces con una sola implementación (yagni), pero siguen la convención del repo para mockear `SettingsReader`; no tocar ahora.

### QA-A-11 Mensajes de error del optimizer en inglés y mezcla de registro en preview
- Severidad: Baja
- Esfuerzo: S (<1h)
- Referencia: ISO 25010 Maintainability/Analysability (convención del proyecto: mensajes en español)
- Evidencia: optimizer/exception/SchedulingException.java:14,18 (título `"Solver error"`); optimizer/service/impl/OptimizerServiceImpl.java:127,131 (`"Optimization interrupted"`, `"Error during optimization: ..."`); preview/service/impl/PreviewServiceImpl.java:149, preview/service/impl/PreviewEngine.java:97,199 y preview/validator/PreviewValidator.java:46 (mensajes en minúscula y con jerga "target").
- Verificación: Confirmado
- Problema: estos mensajes llegan al ProblemDetail que ve el front.
- Arreglo: traducirlos y capitalizarlos.

### QA-A-12 Fecha y hora actual leídas con LocalDate.now()/Instant.now() en 20 sitios
- Severidad: Baja
- Esfuerzo: M (<1 día)
- Referencia: ISO 25010 Maintainability/Testability
- Evidencia: grep `now()` en los cinco módulos: OccurrenceSlotDto.java:22, Occurrence.java:75, AllocationServiceImpl.java:96,159, AllocationValidator.java:124, RoomRequestExpiryServiceImpl.java:41,46, ItemConsistency.java:85,91, RecurringEventReconciler.java:34, entre otros. El módulo dashboard sí inyecta `Clock` (DashboardDateLimits.java:16).
- Verificación: Confirmado
- Problema: la lógica "ya pasó / no se puede modificar" depende del reloj del sistema, así que los tests deben usar fechas relativas y no pueden fijar la hora. La zona sale de `TimeZone.setDefault` en SigaApplication.java:14 (Buenos Aires en runtime, UTC en tests por pom.xml:254,262), por lo que el mismo código da resultados distintos entre test y producción alrededor de las 21:00.
- Arreglo: inyectar `Clock` en OccurrenceSlotDto/Occurrence (hoy métodos de record/entidad) es un cambio grande; dejar como deuda y empezar por los servicios (Expiry, Validator, Reconciler).

### QA-A-13 El historial de asignaciones por edificio hereda la visibilidad de otra asignación
- Severidad: Baja
- Esfuerzo: S (<1h)
- Referencia: ISO 25010 Security/Confidentiality
- Evidencia: src/main/java/ar/edu/utn/frc/siga/allocation/service/impl/AllocationAuditHistoryServiceImpl.java:72-89.
- Verificación: Plausible
- Problema: `previousRevisionInScope` es una sola variable para toda la lista. Una revisión de baja (snapshot null) hereda el flag de la revisión anterior de la lista, que puede ser de otra ocurrencia del mismo evento. Si las revisiones de dos ocurrencias se intercalan, una baja fuera del alcance del actor puede mostrarse. No verifiqué el orden real que devuelve `RevisionReader`.
- Arreglo: mantener el flag por `allocationId` (Map) como ya se hace con `lastClassroomByAllocation`; para la baja, usar la última aula conocida de esa asignación.

### QA-A-14 El optimizer descarta ocupaciones que comparten aula, fecha e inicio
- Severidad: Baja
- Esfuerzo: S (<1h)
- Referencia: ISO 25010 Functional suitability/Functional correctness
- Evidencia: src/main/java/ar/edu/utn/frc/siga/optimizer/service/impl/OptimizerServiceImpl.java:88-96 (planningId `occupied:<aula>:<fecha>:<inicio>`, el duplicado se descarta con WARN).
- Verificación: Plausible
- Problema: dos ocupaciones con mismo inicio y distinto fin en la misma aula (posibles con orígenes IMPORTED/SYSACAD, que no validan solapamiento) colapsan en la primera. Si la descartada es la más larga, el solver puede ubicar un evento nuevo en su tramo final.
- Arreglo: incluir `endTime` en el planningId.

## Resumen

1. Sin comentarios `ponytail:` en el alcance; sin "assignation" ni identificadores fuera de inglés.
2. Los problemas con más impacto son de rendimiento: el solver en PHASE_ASSERT en producción (QA-A-01) y tres lecturas de ocupación por request de conflictos (QA-A-02).
3. La validación de solapamiento carga todas las ocurrencias del rango (QA-A-03); en dev hay 74.698 ocurrencias en nueve meses.
4. No encontré @Transactional mal ubicado con efecto funcional; hay una self-invocation inofensiva en PreviewEngine (QA-A-05).
5. Concurrencia de asignaciones correcta: lock advisory por aula en orden ascendente, UNIQUE(id_ocurrencia) en `asignacion_aula`, @Version en RoomRequestItem.
6. Fronteras de Modulith: las dependencias de los cinco módulos pasan por tipos `@NamedInterface("api")`; no vi acceso a internals (el hilo principal corre ModularityTests).
7. Sobre-ingeniería menor: duplicación y código muerto (QA-A-06 a QA-A-10).
8. Mensajes en inglés del optimizer (QA-A-11) y reloj del sistema sin inyección (QA-A-12) quedan como deuda.
9. Dos puntos de negocio sin resolver van en Preguntas.

Conteo: Crítica 0, Alta 0, Media 3, Baja 11.

## Preguntas para el usuario

1. updateUniqueEvent (events/service/impl/AcademicEventServiceImpl.java:509-557) cambia fecha, hora y duración de un evento único sin revalidar solapamiento ni permisos del aula ya asignada, y no impide mover el evento a una fecha pasada (solo valida que la ocurrencia actual no sea pasada). No encontré en la wiki una regla que lo cubra (Eventos.md no habla de edición). Opciones: (a) es intencional, el usuario resuelve el choque después con la vista de conflictos; (b) debe revalidar como `reallocate`. Si es (b), pasa a Media, esfuerzo M.
2. DDR-001 dice que anular una solicitud "esté Pendiente o ya Resuelta" libera el aula, pero RoomRequestStatus.allows(RESOLVED -> CANCELLED) devuelve false y `cancel` lanza RoomRequestAlreadyNotifiedException (RoomRequestResolutionServiceImpl.java:148-150). Opciones: el DDR quedó desactualizado, o falta implementar cancelar una solicitud resuelta. Si falta, es hallazgo funcional Alto.
3. DirectEventDraftValidator.java:53 decide por rol (`hasGlobalRole(actor, SUBSECRETARIA)`) quién puede asignar aulas sin computadoras, en vez de un permiso PERM_*. ¿Se acepta como excepción del patrón RBAC o va como hallazgo de seguridad para el grupo de auth?
