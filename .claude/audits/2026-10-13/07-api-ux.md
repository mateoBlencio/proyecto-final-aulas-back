# Dimensión 7: API y experiencia de usuario (backend)

Commit auditado: 22c693f2. Excluido: `sysacad`. Base de rutas: context-path `/api`, `siga.api.base-path=/v1` (salvo `/auth`).
Permiso: el `@PreAuthorize` del método pisa al de clase. "Público" = `permitAll` en `SecurityConfig.java:66-70`.

## Inventario de endpoints

| Método | Path | Controlador | Permiso | Paginado |
|---|---|---|---|---|
| POST | /auth/login | AuthController | Público | - |
| POST | /auth/refresh | AuthController | Público | - |
| POST | /auth/logout | AuthController | Público | - |
| POST | /v1/users | UserController | USER_MANAGE | - |
| PATCH | /v1/users/{id}/enabled | UserController | USER_MANAGE | - |
| GET | /v1/users | UserController | USER_READ | Sí (15) |
| GET | /v1/users/disabled | UserController | USER_MANAGE | Sí (15) |
| GET | /v1/users/with-role | UserController | USER_READ | No |
| POST | /v1/users/{id}/role-assignments | UserController | ROLE_ASSIGN | - |
| DELETE | /v1/users/{id}/role-assignments/{assignmentId} | UserController | ROLE_ASSIGN | - |
| GET | /v1/academic-periods | AcademicPeriodController | ACADEMIC_READ | Sí (20) |
| GET | /v1/academic-periods/{id} | AcademicPeriodController | ACADEMIC_READ | - |
| PUT | /v1/academic-periods/{id} | AcademicPeriodController | ACADEMIC_PERIOD_UPDATE | - |
| PUT/DELETE | /v1/academic-periods/{id}/activation | AcademicPeriodController | ACADEMIC_ACTIVATE | - |
| GET | /v1/commissions | CommissionController | ACADEMIC_READ | Sí (20) |
| GET | /v1/commissions/{id} | CommissionController | ACADEMIC_READ | - |
| PUT/DELETE | /v1/commissions/{id}/activation | CommissionController | ACADEMIC_ACTIVATE | - |
| GET | /v1/study-plans | StudyPlanController | ACADEMIC_READ | Sí (20) |
| GET | /v1/study-plans/{id} | StudyPlanController | ACADEMIC_READ | - |
| PUT/DELETE | /v1/study-plans/{id}/activation | StudyPlanController | ACADEMIC_ACTIVATE | - |
| GET | /v1/subject-commissions | SubjectCommissionController | ACADEMIC_READ | Sí (20) |
| PUT/DELETE | /v1/subject-commissions/{subjectId}/{commissionId}/activation | SubjectCommissionController | ACADEMIC_ACTIVATE | - |
| GET | /v1/subjects | SubjectController | ACADEMIC_READ | Sí (20) |
| GET | /v1/subjects/{id} | SubjectController | ACADEMIC_READ | - |
| PUT/DELETE | /v1/subjects/{id}/activation | SubjectController | ACADEMIC_ACTIVATE | - |
| GET | /v1/specialties | SpecialtyController | ACADEMIC_READ | Sí (20) |
| GET | /v1/specialties/{id} | SpecialtyController | ACADEMIC_READ | - |
| GET | /v1/holidays?from&to | HolidayController | ACADEMIC_READ | No (rango) |
| GET | /v1/allocations?date | AllocationController | ALLOCATION_READ | No (un día) |
| GET | /v1/allocations/{id} | AllocationController | ALLOCATION_READ | - |
| GET | /v1/allocations/events/unique | AllocationController | ALLOCATION_READ | No |
| GET | /v1/allocations/history | AllocationController | ALLOCATION_HISTORY_READ | No |
| GET | /v1/allocations/conflicts | AllocationController | CONFLICT_READ | Sí (20) |
| POST | /v1/allocations | AllocationController | ALLOCATION_WRITE | - |
| PUT | /v1/allocations | AllocationController | ALLOCATION_WRITE | - |
| POST | /v1/allocations/impact | AllocationController | ALLOCATION_WRITE | - |
| DELETE | /v1/allocations (con body) | AllocationController | ALLOCATION_WRITE | - |
| GET | /v1/audit | AuditRegistryController | AUDIT_READ | Sí (20) |
| GET | /v1/audit/export | AuditRegistryController | AUDIT_READ | CSV |
| GET | /v1/audit/operations/{operationId} | AuditRegistryController | AUDIT_READ | Sí (20) |
| GET | /v1/audit/operations/{operationId}/chain | AuditRegistryController | AUDIT_READ | - |
| GET | /v1/audit/revisions/{revision} | AuditRegistryController | AUDIT_READ | Sí (20) |
| GET | /v1/audit/entities/{entityType}/{recordId} | AuditRegistryController | AUDIT_READ | Sí (20) |
| GET | /v1/audit/entity-types | AuditRegistryController | AUDIT_READ | No |
| GET | /v1/audit/archive/cycles | AuditArchiveController | AUDIT_READ | No |
| GET | /v1/audit/archive/cycles/{cycle}/export | AuditArchiveController | AUDIT_READ | Archivo |
| GET | /v1/events | AcademicEventController | EVENT_READ | Sí (20) |
| GET | /v1/events/{id} | AcademicEventController | EVENT_READ | - |
| GET | /v1/events/{id}/occurrences | AcademicEventController | EVENT_READ | No |
| GET | /v1/events/{id}/history | AcademicEventController | EVENT_READ | No |
| GET | /v1/events/occurrences/{occurrenceId}/history | AcademicEventController | EVENT_READ | No |
| POST | /v1/events/recurring | AcademicEventController | EVENT_MANAGE | - |
| GET | /v1/events/unique | AcademicEventController | EVENT_READ | No |
| POST | /v1/events/unique | AcademicEventController | EVENT_MANAGE | - |
| PUT | /v1/events/unique/{id} | AcademicEventController | EVENT_MANAGE | - |
| POST | /v1/events/occurrences/{occurrenceId}/release | AcademicEventController | OCCURRENCE_RELEASE | - |
| POST | /v1/events/occurrences/{occurrenceId}/request-room | AcademicEventController | OCCURRENCE_REQUEST_ROOM | - |
| POST | /v1/events/direct/available-classrooms | DirectEventController | EVENT_DIRECT_CREATE | - |
| POST | /v1/events/direct/suggestion | DirectEventController | EVENT_DIRECT_CREATE | - |
| POST | /v1/events/direct | DirectEventController | EVENT_DIRECT_CREATE | - |
| POST | /v1/imports (multipart) | IngestController | INGEST_RUN | - |
| POST | /v1/previews | PreviewController | PREVIEW_RUN | - |
| GET | /v1/previews/{previewId} | PreviewController | PREVIEW_RUN | - |
| POST | /v1/previews/{previewId}/confirm | PreviewController | PREVIEW_RUN | - |
| POST | /v1/previews/reallocation-suggestion | PreviewController | PREVIEW_RUN + ALLOCATION_WRITE | - |
| POST | /v1/previews/reallocation-suggestion/{suggestionId}/confirm | PreviewController | PREVIEW_RUN + ALLOCATION_WRITE | - |
| GET | /v1/room-requests/catalog/specialties | RoomRequestCatalogController | Público | No |
| GET | /v1/room-requests/catalog/subjects?specialtyCode | RoomRequestCatalogController | Público | No |
| GET | /v1/room-requests/catalog/commissions?subjectId | RoomRequestCatalogController | Público | No |
| GET | /v1/room-requests/catalog/classrooms | RoomRequestCatalogController | Público | No |
| GET | /v1/room-requests/catalog/commission-schedule | RoomRequestCatalogController | Público | - |
| POST | /v1/room-requests | RoomRequestController | Público | - |
| GET | /v1/room-requests/items | RoomRequestController | ROOM_REQUEST_READ | Sí (20) |
| GET | /v1/room-requests/items/status-counts | RoomRequestController | ROOM_REQUEST_READ | No |
| GET | /v1/room-requests/items/{id} | RoomRequestController | ROOM_REQUEST_READ | - |
| POST | /v1/room-requests/items/{id}/assign | RoomRequestController | ROOM_REQUEST_WRITE | - |
| POST | /v1/room-requests/items/{id}/cancel | RoomRequestController | ROOM_REQUEST_WRITE | - |
| GET | /v1/room-requests/items/{id}/allowed-classrooms | RoomRequestController | ROOM_REQUEST_READ | No |
| POST | /v1/room-requests/items/{id}/derive | RoomRequestController | ROOM_REQUEST_WRITE | - |
| POST | /v1/room-requests/items/{id}/return | RoomRequestController | ROOM_REQUEST_WRITE | - |
| POST | /v1/room-requests/items/{id}/notify | RoomRequestController | ROOM_REQUEST_WRITE | - |
| GET | /v1/room-requests/items/{id}/candidate-buildings | RoomRequestController | ROOM_REQUEST_READ | No |
| POST | /v1/room-requests/items/{id}/suggestion | RoomRequestController | ROOM_REQUEST_WRITE | - |
| POST | /v1/room-requests/items/{id}/suggestion/{suggestionId}/confirm | RoomRequestController | ROOM_REQUEST_WRITE | - |
| GET | /v1/settings | SettingsController | SETTINGS_READ | No (mapa) |
| GET | /v1/settings/{key} | SettingsController | SETTINGS_READ | - |
| PUT | /v1/settings/{key} | SettingsController | SETTINGS_WRITE | - |
| PUT | /v1/settings | SettingsController | SETTINGS_WRITE | - |
| GET | /v1/buildings | BuildingController | BUILDING_READ | Sí (20) |
| PUT/DELETE | /v1/buildings/{id}/activation | BuildingController | BUILDING_ACTIVATE | - |
| PATCH | /v1/buildings/activation | BuildingController | BUILDING_ACTIVATE | - |
| POST | /v1/classrooms | ClassroomController | CLASSROOM_CREATE | - |
| GET | /v1/classrooms/{id} | ClassroomController | CLASSROOM_READ | - |
| GET | /v1/classrooms | ClassroomController | CLASSROOM_READ | Sí (20) |
| PUT | /v1/classrooms/{id} | ClassroomController | CLASSROOM_UPDATE | - |
| PUT | /v1/classrooms/{id}/details | ClassroomController | CLASSROOM_UPDATE | - |
| DELETE | /v1/classrooms/{id} | ClassroomController | CLASSROOM_DELETE | - |
| PUT/DELETE | /v1/classrooms/{id}/activation | ClassroomController | CLASSROOM_ACTIVATE | - |
| GET | /v1/classroom-types | ClassroomTypeController | CLASSROOM_TYPE_READ | Sí (20) |
| GET | /v1/classroom-types/{id} | ClassroomTypeController | CLASSROOM_TYPE_READ | - |
| POST | /v1/classroom-types | ClassroomTypeController | CLASSROOM_TYPE_MANAGE | - |
| PUT | /v1/classroom-types/{id} | ClassroomTypeController | CLASSROOM_TYPE_MANAGE | - |
| PUT/DELETE | /v1/classroom-types/{id}/activation | ClassroomTypeController | CLASSROOM_TYPE_MANAGE | - |
| GET | /v1/resource-types | ResourceTypeController | RESOURCE_TYPE_READ | Sí (20) |
| GET | /v1/resource-types/{id} | ResourceTypeController | RESOURCE_TYPE_READ | - |
| POST | /v1/resource-types | ResourceTypeController | RESOURCE_TYPE_MANAGE | - |
| PUT | /v1/resource-types/{id} | ResourceTypeController | RESOURCE_TYPE_MANAGE | - |
| PUT/DELETE | /v1/resource-types/{id}/activation | ResourceTypeController | RESOURCE_TYPE_MANAGE | - |
| GET | /v1/dashboard/week | DashboardController | DASHBOARD_READ | - |
| GET | /v1/dashboard/period | DashboardController | DASHBOARD_READ | - |
| GET | /v1/dashboard/badges | DashboardController | DASHBOARD_READ | - |
| GET | /v1/dashboard/room-requests | DashboardController | DASHBOARD_READ | - |
| GET | /v1/dashboard/allocation-trend | DashboardController | DASHBOARD_READ | - |
| GET | /v1/dashboard/reports/{month} | DashboardController | DASHBOARD_READ | - |
| POST | /v1/dashboard/reports/{month}/regeneration | DashboardController | DASHBOARD_SNAPSHOT_REGENERATE | - |

Lo que ya está bien y no se reporta: un único `@RestControllerAdvice` (`GlobalExceptionHandler`) que devuelve `ProblemDetail` (RFC 9457) también desde el filtro de seguridad y el rate limit (`ProblemDetailResponseWriter`, `application/problem+json`); el 500 genérico no expone stack trace; `max-page-size: 200` global (`application.yaml:13`); `Page` se serializa vía DTO estable (`PageSerializationConfig`); ningún controlador devuelve entidades JPA; paths en inglés, plural y kebab-case; 409 para optimistic lock.

## Hallazgos

### API-01 Mensajes de 404 y títulos de error en inglés y con nombres de clase
- Severidad: Media
- Esfuerzo: S
- Referencia: Nielsen 9; RFC 9457 §3.1.3-3.1.4 (`title`/`detail` legibles por humanos)
- Evidencia: `common/exception/ResourceNotFoundException.java:13,17` (60 usos, más 25 vía `common/util/Finder.java:18`); títulos en inglés en 27 subclases de `SigaAppException` (ej. `allocation/exception/AllocationConflictException.java:16` "Allocation error", `auth/exception/UserDomainException.java:14` "User domain error"); `GlobalExceptionHandler.java:101` "Concurrent modification"; `auth/security/RestAuthenticationEntryPoint.java:19` detail en inglés; detalles en inglés en `auth/service/impl/RoleAssignmentServiceImpl.java:77-103`, `UserServiceImpl.java:54,58`, `ClassroomServiceImpl.java:280`, `ClassroomFeatureWriter.java:103`.
- Verificación: Confirmado
- Problema: Un 404 llega como `"AcademicPeriod not found with id: 5"`: inglés, con el nombre de la clase Java y sin decir qué hacer. El mismo `ProblemDetail` mezcla `title` en inglés con `detail` en español (ej. 403 "Forbidden" + "No tenés permiso..."), así que el front no puede mostrar `title` tal cual.
- Arreglo: En `ResourceNotFoundException.of` traducir el nombre del recurso con un mapa (`"AcademicPeriod" -> "período académico"`) y armar `"No existe el período académico 5."`; pasar los títulos de las excepciones a español; traducir los ~10 `detail` en inglés listados. Si el front necesita una clave estable, agregar `code` como propiedad (ya existe el patrón en `GlobalExceptionHandler.java:103`).

### API-02 El 500 del solver expone el mensaje interno de la excepción
- Severidad: Media
- Esfuerzo: S
- Referencia: OWASP ASVS v5.0.0 V16 (manejo de errores: mensajes genéricos al cliente); Nielsen 9
- Evidencia: `optimizer/service/impl/OptimizerServiceImpl.java:141` arma `"Error during optimization: " + cause.getMessage()`; `GlobalExceptionHandler.java:37` copia `ex.getMessage()` al `detail`.
- Verificación: Confirmado
- Problema: Cualquier falla de Timefold (NPE, error de Hibernate dentro del solver, `IllegalStateException` con nombres de clases) llega al cliente como `detail` de un 500. El usuario ve jerga y no sabe si reintentar.
- Arreglo: Detail fijo en español ("No se pudo calcular la asignación automática. Reintentá en unos minutos o reducí la cantidad de eventos.") y dejar `cause` solo en el log, que ya existe en la línea 140.

### API-03 `sort` inválido en listados sin lista blanca termina en 500
- Severidad: Media
- Esfuerzo: S
- Referencia: Zalando API Guidelines #137 (parámetros de orden); RFC 9110 §15.5.1 (400)
- Evidencia: Solo `space/specification/ClassroomListSort.java:41-45` y `roomrequest/specification/RoomRequestItemSort.java` validan `sort` (test `ClassroomListApiIntegrationTest.java:107`). `BuildingController`, `AcademicPeriodController`, `SubjectController`, `CommissionController`, `StudyPlanController`, `SpecialtyController`, `SubjectCommissionController`, `AcademicEventController`, `ClassroomTypeController`, `ResourceTypeController` y `UserController` pasan el `Pageable` crudo al repositorio. `GlobalExceptionHandler` no mapea `PropertyReferenceException` ni `InvalidDataAccessApiUsageException`, que no implementan `ErrorResponse`, así que caen en `handleUnexpected` (línea 113-114).
- Verificación: Plausible (handler ausente confirmado por lectura; no reproduje la request)
- Problema: `GET /v1/buildings?sort=nombre` responde 500 "Error interno" en vez de 400 que diga qué campos se pueden ordenar, y deja un `log.error` con stack trace por un error del cliente.
- Arreglo: Agregar un `@ExceptionHandler(PropertyReferenceException.class)` (y desempaquetar `InvalidDataAccessApiUsageException` cuando su causa sea esa) que devuelva 400 "Campo de ordenamiento inválido: 'x'". Sumar un test por un controlador sin lista blanca.

### API-04 Duplicados devuelven 400 y la carrera del duplicado devuelve 500
- Severidad: Media
- Esfuerzo: M
- Referencia: RFC 9110 §15.5.10 (409 Conflict); Zalando #150 (uso de 409)
- Evidencia: chequeo previo con 400: `auth/service/impl/UserServiceImpl.java:58`, `space/service/impl/ClassroomServiceImpl.java:82`, `ResourceTypeServiceImpl.java:87`, `ClassroomTypeServiceImpl.java:113`, `RoleAssignmentServiceImpl.java:103`. Solo `academic/service/impl/AcademicPeriodServiceImpl.java:109` atrapa `DataIntegrityViolationException`; `GlobalExceptionHandler` no la mapea.
- Verificación: Confirmado (códigos y handler ausente); Plausible (500 en la carrera, depende de que haya índice único)
- Problema: "Ya existe un aula con el número X" sale como 400, igual que un error de formato, y el front no puede distinguir conflicto de validación. Si dos requests pasan el chequeo a la vez, la violación de la constraint termina en 500 genérico.
- Arreglo: Usar 409 para esos cinco casos (una subclase `DuplicateResourceException` o un parámetro de status) y agregar un handler de `DataIntegrityViolationException` que devuelva 409 con detail genérico en español, sin el mensaje SQL.

### API-05 Listas sin tope en el alta pública de solicitudes y en lotes
- Severidad: Media
- Esfuerzo: S
- Referencia: OWASP API4:2023 (Unrestricted Resource Consumption)
- Evidencia: `POST /v1/room-requests` es público (`auth/config/SecurityConfig.java:69`); `roomrequest/dto/request/CreateConferenceDto.java:14`, `CreateFinalExamDto.java:14`, `CreateOtherDto.java:14`, `CreatePartialExam*Dto.java`, `Create*RoomChangeDto.java:15` declaran `items` solo con `@NotEmpty`; `FreeFormItemDto.java:29` y `ScheduledItemDto.java:26` `preferredClassroomIds` sin `@Size`. Autenticados sin tope: `allocation/dto/request/AllocationBatchRequestDto.java:9`, `DeallocationBatchRequestDto.java:9`, `AllocationItemRequestDto.java:12`, `preview/dto/request/PreviewRequestDto.java:13,19`, `ConfirmPreviewRequestDto.java:14`, `roomrequest/dto/request/AssignRoomRequestItemDto.java:7`. Contraste: `ReallocationSuggestionRequestDto.java:14,18` y `SuggestRoomRequestItemDto.java:9` sí tienen `@Size(max = 200)`.
- Verificación: Confirmado
- Problema: Un anónimo puede mandar una solicitud con miles de ítems; cada uno genera filas y validaciones contra la base. En los endpoints autenticados el riesgo es menor, pero el error llega tarde (después de cargar todo) en vez de como 400 inmediato.
- Arreglo: `@Size(max = N)` con mensaje en español en esas listas. N lo define negocio (ej. 50 ítems por solicitud, 20 aulas preferidas, 500 en lotes de asignación).

### API-06 Mensajes de Bean Validation sin texto propio y 400 de validación sin `detail`
- Severidad: Baja
- Esfuerzo: S
- Referencia: Nielsen 9; RFC 9457 §3.1.4
- Evidencia: 104 de ~109 constraints en DTOs de request no tienen `message` (ej. `auth/dto/request/CreateUserRequestDto.java:8-11`, `events/dto/request/CreateUniqueEventRequestDto.java:15-26`); no hay `ValidationMessages*.properties` ni locale fijo en `application.yaml`. `GlobalExceptionHandler.java:51` pone `detail = null`. Para `@AssertTrue` sobre métodos (`AllocationItemRequestDto.java:18,26`) la clave en `errors` es el nombre de la propiedad Java derivada del método.
- Verificación: Plausible (idioma: Spring interpola con el locale del request, que sale de `Accept-Language`; no lo reproduje)
- Problema: El texto de `errors` depende del header `Accept-Language` del cliente: desde un navegador en español sale "no debe estar vacío", desde Bruno o un script sin header puede salir "must not be blank". El 400 no trae una frase general que el front pueda mostrar.
- Arreglo: Fijar `spring.web.locale: es_AR` y `spring.web.locale-resolver: fixed`; poner `detail` "Revisá los campos marcados." en `handleValidation`. Mensajes propios solo donde el default no alcance (ej. `@Email`, `@FutureOrPresent`).

### API-07 Errores del framework salen con `detail` en inglés y tipos Java
- Severidad: Baja
- Esfuerzo: M
- Referencia: Nielsen 9; RFC 9457
- Evidencia: `GlobalExceptionHandler.java:109-111` devuelve `errorResponse.getBody()` tal cual para `MissingServletRequestParameterException`, `HttpRequestMethodNotSupportedException`, `HandlerMethodValidationException`, `NoResourceFoundException`, etc.; `GlobalExceptionHandler.java:81-83` arma "debe ser de tipo Long/LocalDate/YearMonth".
- Verificación: Confirmado
- Problema: `GET /v1/holidays` sin `from` responde "Required parameter 'from' is not present."; `/v1/dashboard/reports/2026-13` responde "debe ser de tipo YearMonth". El usuario no sabe qué formato usar.
- Arreglo: Hacer que `GlobalExceptionHandler` extienda `ResponseEntityExceptionHandler` y cargar `messages.properties` con las claves `problemDetail.<FQCN>` en español, o mapear a mano los 3-4 casos frecuentes. Para tipo inválido, reemplazar el nombre de clase por un ejemplo de formato (`yyyy-MM-dd`, `yyyy-MM`, número entero).

### API-08 OpenAPI: esquema de seguridad sin aplicar y `UserController` sin documentar
- Severidad: Baja
- Esfuerzo: S
- Referencia: OpenAPI 3.1 §4.8.30 (Security Requirement); ISO 25010 Usability/Learnability
- Evidencia: `auth/config/OpenApiSecurityConfig.java` declara `bearerAuth`, pero no hay ningún `@SecurityRequirement` en el código ni `security` en `common/config/OpenApiConfig.java`. `auth/controller/UserController.java` no tiene `@Tag` ni `@Operation` (7 endpoints). Solo `AuditRegistryController` y `AuditArchiveController` usan `@ApiResponse`; el resto describe los códigos de error en texto libre dentro de `description`. `OpenApiConfig` titula la API "Classroom Allocation API" en vez de SIGA.
- Verificación: Confirmado
- Problema: En Swagger UI el botón Authorize no adjunta el token a ninguna operación, así que probar desde ahí da 401. El esquema `ProblemDetail` no figura como respuesta de error, y un cliente generado no lo conoce.
- Arreglo: `@OpenAPIDefinition(security = @SecurityRequirement(name = "bearerAuth"))` y `@SecurityRequirements()` vacío en `AuthController`; agregar `@Tag`/`@Operation` en `UserController`. Documentar `ProblemDetail` como respuesta por defecto con un `OpenApiCustomizer` global (deuda, M).

### API-09 201 sin header `Location`
- Severidad: Baja
- Esfuerzo: S
- Referencia: RFC 9110 §15.3.2; Zalando #148
- Evidencia: 0 usos de `ResponseEntity.created(...)`; los altos usan `status(HttpStatus.CREATED).body(...)`: `UserController.java:51,102`, `ClassroomController.java:50`, `ClassroomTypeController.java:67`, `ResourceTypeController.java:67`, `AcademicEventController.java:118,144`, `RoomRequestController.java:73`, `DirectEventController.java:74`, `AllocationController.java:141`.
- Verificación: Confirmado
- Problema: El cliente obtiene el id del cuerpo, que funciona, pero no cumple la convención. Impacto bajo porque el front ya lee el cuerpo.
- Arreglo: Usar `ResponseEntity.created(uri)` en los altos de recurso único (usuario, aula, tipos, evento único). En lotes (`POST /allocations`, `POST /events/direct`) dejarlo como está.

### API-10 `DELETE /v1/allocations` con cuerpo y `PUT` sobre la colección
- Severidad: Baja
- Esfuerzo: M
- Referencia: RFC 9110 §9.3.5 (contenido en DELETE sin semántica definida); Zalando #148
- Evidencia: `allocation/controller/AllocationController.java:144-156` (`PUT /allocations` para reasignar en lote) y `:181-186` (`DELETE /allocations` con `@RequestBody`).
- Verificación: Confirmado
- Problema: Algunos proxies y clientes HTTP descartan el cuerpo de un DELETE; si eso pasa el backend recibe un body vacío y responde 400. `PUT` sobre la colección sugiere reemplazar todas las asignaciones.
- Arreglo: Deuda, rompe el contrato con el front. Si se toca: `POST /v1/allocations/deallocations` y `POST /v1/allocations/reallocations`.

### API-11 Inconsistencias menores de contrato
- Severidad: Baja
- Esfuerzo: S
- Referencia: Zalando #160 (paginación consistente), #169 (formato de fechas RFC 3339)
- Evidencia: activación con `PUT/DELETE /{id}/activation` en 9 recursos, `PATCH /users/{id}/enabled` con body (`UserController.java:54`) y `PATCH /buildings/activation` en lote (`BuildingController.java:82`); page size por defecto 15 en `UserController.java:67,77` contra 20 en el resto; fechas con zona (`Instant`, 9 DTOs) y sin zona (`LocalDateTime` en `audit/dto/response/AuditLogEntryDto.java:39`, `RevisionDto.java:11`, `AuditArchivedCycleDto.java:10`).
- Verificación: Confirmado
- Problema: El front necesita reglas distintas por recurso para la misma operación. Las fechas de auditoría salen sin offset y el cliente tiene que suponer la zona del servidor.
- Arreglo: Unificar el default de usuarios en 20 (S). Pasar los `LocalDateTime` de auditoría a `Instant` u `OffsetDateTime` (S, coordinar con front). La forma de activar usuarios queda como deuda.

### API-12 Listados de eventos únicos sin paginar ni filtrar
- Severidad: Baja
- Esfuerzo: M
- Referencia: OWASP API4:2023; Zalando #159
- Evidencia: `events/service/impl/AcademicEventServiceImpl.java:501-503` hace `uniqueEventRepository.findAll()`; lo exponen `GET /v1/events/unique` (`AcademicEventController.java:121-130`) y `GET /v1/allocations/events/unique` (`AllocationController.java:85-97`), que además compone aula por cada evento y filtra por edificio en memoria. Dev-snapshot hoy tiene 1 fila en `evento_unico` (`SELECT count(*) FROM evento_unico`).
- Verificación: Confirmado
- Problema: La respuesta crece con cada parcial y mesa especial de todos los años, sin corte por período. Hoy no molesta por el volumen; en dos o tres cuatrimestres el listado trae miles de filas por request.
- Arreglo: Deuda. Agregar filtro por rango de fechas o período (S) antes que paginar (M, cambia el contrato).

## Resumen

El formato de error es consistente: un solo `@RestControllerAdvice` con `ProblemDetail` RFC 9457, también para 401/403/429 escritos desde filtros, y el 500 no filtra stack traces. Los problemas están en el contenido de los mensajes: títulos y 404 en inglés con nombres de clase (API-01), un 500 del solver que copia el mensaje interno (API-02) y errores del framework sin traducir (API-07). Tres casos devuelven el código equivocado: `sort` inválido da 500 en 11 controladores (API-03), los duplicados dan 400 y la carrera da 500 (API-04). En OWASP API4, el alta pública de solicitudes acepta listas sin tope (API-05); el tope global de página (200) ya está. Para "arreglar ahora": API-01, API-02, API-03, API-05 y la parte S de API-06 (locale fijo); el resto es deuda.

Conteo: Crítica 0, Alta 0, Media 5 (API-01 a API-05), Baja 7 (API-06 a API-12).

## Preguntas para el usuario

- API-05: ¿cuántos ítems por solicitud de aula y cuántas aulas preferidas tiene sentido aceptar? Hace falta para fijar los `@Size`.
- API-10 y API-11: ¿el front puede absorber cambios de contrato este cuatrimestre, o todo lo que rompa el contrato va a deuda?
