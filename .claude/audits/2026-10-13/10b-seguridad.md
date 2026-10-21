# Seguridad del módulo sysacad (commit 22c693f2)

Alcance: `sysacad/**` y los `sync*` de space, academic, events, allocation. Solo lectura; no se corrió mvnw ni se consultó la base. Sin lo ya informado en `.claude/audits/2026-10-13/04-seguridad.md`. Ningún secreto impreso: la clave de Sysacad vive en la variable `SYSACAD_API_KEY` (sin default en `application.yaml:182`, así que la app no arranca sin ella).

## Resumen de hallazgos

| ID | Sev | Estado | Tema |
|---|---|---|---|
| SYS-SEC-01 | HIGH | CONFIRMED | Vistas con filas pero sin claves válidas marcan todo como no vigente (guarda `requireRows` insuficiente) |
| SYS-SEC-02 | HIGH | CONFIRMED | EVENTOS: si ninguna fila resuelve comisión, `markRecurringEventsAbsent` da de baja todos los eventos |
| SYS-SEC-03 | MEDIUM | CONFIRMED | `lastError` persiste y expone `e.getMessage()` crudo (SQL, clases, cuerpo upstream) a `PERM_SYSACAD_READ` |
| SYS-SEC-04 | MEDIUM | CONFIRMED | Sin lock entre cron y disparo manual; el manual repetido recarga la API upstream sin límite propio |
| SYS-SEC-05 | MEDIUM | POTENTIAL | Sin tope de tamaño de respuesta y timeout de lectura que no acota la descarga del cuerpo |
| SYS-SEC-06 | LOW | CONFIRMED | Datos externos con caracteres de control van a logs y a la base sin sanear (CRLF en logs) |
| SYS-SEC-07 | LOW | CONFIRMED | Disparo duplicado devuelve 200 SUCCESS sin avisar que no ejecutó nada |
| SYS-SEC-08 | LOW | RECOMMENDATION | Sin validación de `base-url` (esquema https) ni de longitudes/rangos antes de persistir |
| SYS-SEC-09 | INFO | RECOMMENDATION | Caso 8 del equipo: la tabla `sysacad_origen` con payload crudo necesita tope y control de acceso |

Conteo: CRITICAL 0, HIGH 2, MEDIUM 3, LOW 3, INFO 1.

## Hallazgos

[SECURITY] HIGH
ID: SYS-SEC-01
Confidence: HIGH
Location: `sysacad/internal/sync/ViewSyncRunner.java:15-20`; `space/service/impl/BuildingServiceImpl.java:118-160` (`markAbsent`); `ClassroomServiceImpl.java:286-340`; `academic/service/impl/CommissionServiceImpl.java:134-190,260-274`
Type: Integridad de datos de API externa (API10:2023, ASVS V2 validación de negocio, A08:2025)
Problem: `requireRows` solo rechaza la lista vacía. Si la vista trae filas pero todas sin clave (código nulo, edificio ausente, nombre en blanco), los `syncBuildings/syncClassrooms/syncCommissions` las ignoran con WARN, `incoming` queda vacío y `markAbsent` pone `sysacadEnabled=false` en todo lo existente. Una sola fila válida de entre miles produce el mismo efecto sobre el resto. No hay umbral mínimo ni comparación contra la corrida anterior.
Impact: un payload corrupto o un cambio de esquema en Sysacad (columna renombrada: Jackson `ignoreUnknown` deja los campos en null) deja edificios, aulas y comisiones como no vigentes; el sync de comisiones además arrastra `NOT_IN_SYSACAD` (`Commission.java:59`). El servicio de estado registra éxito con N filas "afectadas".
Recommendation: contar las filas válidas (con clave) tras mapear y abortar con `SysacadEmptyViewException` si es 0, y abortar si las bajas superan un porcentaje configurable (p. ej. 30 %) de los vigentes. Cubierto parcialmente por E1 (contadores de incidencias) de `integridad-baja-logica-auditoria.md`, que cuenta pero no frena; el umbral es lo nuevo. Test pendiente (test-writer): vista con N filas todas con código nulo, resultado esperado: excepción y cero cambios de vigencia.

[SECURITY] HIGH
ID: SYS-SEC-02
Confidence: HIGH
Location: `sysacad/internal/sync/AcademicEventSyncService.java:56-94`; `events/service/impl/AcademicEventServiceImpl.java:422-436`
Type: Integridad / baja masiva por dato ausente
Problem: `requireRows` se aplica a los eventos ya mapeados, pero después cada fila pasa por `resolver.resolve(...)`; si ninguna resuelve (comisiones sin sincronizar, `COMISIONES` fallida en la misma corrida), `commands` queda vacío, `presentEventIds` es vacío y `markRecurringEventsAbsent` marca como no vigentes todos los `RecurringEvent` con `sysacadHash`. El orden de FK de `resyncAll` continúa tras un fallo de COMISIONES (el `catch` en `SysacadSyncOrchestratorImpl.resyncAll` no corta).
Impact: una falla de una vista previa (o un `Comision`/`Curso` que cambia de formato) apaga todos los eventos recurrentes de Sysacad, y en cascada las asignaciones que cuelgan de ellos.
Recommendation: abortar si `commands.isEmpty()` tras resolver, y no continuar con EVENTOS/ASIGNACIONES si COMISIONES falló en la misma corrida. Mismo umbral porcentual que SYS-SEC-01 sobre `markRecurringEventsAbsent`.

[SECURITY] MEDIUM
ID: SYS-SEC-03
Confidence: HIGH
Location: `ViewSyncRunner.java:29-31`; `SysacadSyncStateServiceImpl.java:44-50`; `SysacadSyncController.java:findState/resyncAll`; `SysacadSyncStateDto.java`
Type: Exposición de información en mensajes de error (ASVS V16 manejo de errores, API3:2023)
Problem: `recordFailure(view, e.getMessage())` guarda hasta 1000 caracteres del mensaje de cualquier RuntimeException y `GET/POST /sysacad/sync` lo devuelve como `lastError`. Esos mensajes incluyen texto de Hibernate/PostgreSQL (nombres de constraint, valores), `RestClientResponseException` (URL de la vista y fragmento del cuerpo upstream) y errores de Jackson con nombres de clase internos.
Impact: cualquier titular de `PERM_SYSACAD_READ` (permiso de lectura, probablemente más amplio que el de sync) ve detalles internos de esquema y de la API de Sysacad. No hay credenciales: la clave va en header y no entra en el mensaje.
Recommendation: persistir un código/categoría (`CONNECTIVITY`, `EMPTY_VIEW`, `DATA`) más un mensaje controlado, y dejar el `getMessage()` completo solo en el log del servidor. Confirmar con business-rules qué roles tienen `SYSACAD_READ`.

[SECURITY] MEDIUM
ID: SYS-SEC-04
Confidence: MEDIUM
Location: `SysacadSyncOrchestratorImpl.java:40-45` (`sync(view)` sin lock) vs `:48-52` (`running` solo en `resyncAll`); `SysacadSyncScheduler.java`; `SysacadSyncController.java` POST
Type: Concurrencia / consumo de recursos (API4:2023)
Problem: el `AtomicBoolean running` solo excluye disparos manuales entre sí. Los 8 crons (todos `0 0 3 * * SUN`, `application.yaml:188-196`) llaman `orchestrator.sync(view)` sin tomarlo, así que un POST manual puede correr a la vez que un cron sobre la misma tabla (dos transacciones upsert concurrentes; riesgo de violación de unicidad o de `markAbsent` sobre lectura vieja). El lock es por JVM, no por base. Además, cada POST secuencial descarga las vistas completas (Horarios ~2 MB) y retiene un hilo HTTP hasta 4 intentos x 120 s por vista; el único límite es el rate limit general de 100 req/min por IP (`RateLimitFilter`), que no protege este endpoint caro. El scheduler de Spring corre con un hilo, lo que serializa los crons entre sí pero no con el request.
Impact: un usuario con `PERM_SYSACAD_SYNC` (o una cuenta comprometida) puede mantener ocupados hilos de Tomcat y la API de Sysacad con POST en serie; carrera cron/manual en el domingo 03:00.
Dato del hilo principal (PIT): quitar `running.set(false)` en `resyncAll` (línea 76) sobrevive, es decir, ningún test unitario verifica la guarda de corridas simultáneas; test pendiente (test-writer): dos `resyncAll` concurrentes, la segunda no ejecuta syncers; tras terminar la primera, una tercera sí ejecuta. La guarda es un `AtomicBoolean` en memoria: no alcanza con más de una instancia (cada JVM tiene el suyo). `docker-compose.deploy.yml` define un servicio por perfil sin réplicas, así que hoy hay una sola instancia por entorno y el riesgo multi-instancia es futuro. Un reinicio resetea el flag, lo que es inocuo porque la transacción en curso se pierde y revierte.
Recommendation: que `sync(view)` use el mismo `running` (o un lock por vista) y que el POST tenga un intervalo mínimo entre ejecuciones (p. ej. 5 min, responder 429 con Retry-After). Multi-instancia: ShedLock o advisory lock de Postgres si se despliega más de una réplica.

[SECURITY] MEDIUM
ID: SYS-SEC-05
Confidence: LOW
Location: `SysacadConfiguration.java:23-35`; `SysacadClient.java:40-45`; `application.yaml:184-187`
Type: Consumo de API sin límites (API10:2023, ASVS V13)
Problem: (a) No hay tope de tamaño de respuesta: `.body(responseType)` deserializa la vista entera a memoria. (b) `readTimeout` en `JdkClientHttpRequestFactory` acota hasta recibir los headers; no está verificado que acote la lectura del cuerpo, por lo que un upstream que gotea bytes podría sostener la conexión sin vencer (hay que probarlo, no lo corrí). (c) Reintentos: 3 con backoff exponencial hasta 30 s y `Retry-After` acotado a 30 s; correcto. TLS: el cliente JDK verifica certificado y host por defecto y no sigue redirects (no se filtra la clave); `base-url` es https en `application.yaml:183`.
Impact: una vista inesperadamente grande o un upstream lento retiene memoria o el hilo del request; la fuente es de confianza parcial (ver SYS-SEC-01), así que el riesgo es de disponibilidad, no de explotación directa.
Recommendation: validar un `Content-Length` máximo configurable o leer con límite, y confirmar con una prueba de integración (servidor que goteó el cuerpo) que el `readTimeout` vence. Anotar el resultado en el hallazgo antes de subir la severidad.

[SECURITY] LOW
ID: SYS-SEC-06
Confidence: HIGH
Location: `SysacadCatalogMapper.java` (`log.warn(... raw.fecha() ...)`, `trimmed`, `curso`); `SpecialtyServiceImpl.java` (log con `command.name()`); `SubjectServiceImpl.java` (variantes con `c.name()`); `trim()` en el mapper
Type: Log injection (ASVS V16 logging, CWE-117)
SpotBugs (hilo principal): 18 CRLF_INJECTION_LOGS en el módulo y 1 IMPROPER_UNICODE en `SysacadCatalogMapper:49` (comparación `equalsIgnoreCase` sin locale), coherentes con este hallazgo.
Problem: `trim()` quita espacios pero no caracteres de control; los valores de Sysacad (código de curso, nombre, fecha, hora) se loguean con `{}` sin saneamiento y el patrón de logback por defecto no escapa CR/LF. Los mismos nombres se guardan tal cual en base y se devuelven por la API.
Impact: una fila con `\r\n` forja líneas de log; requiere comprometer o corromper Sysacad. Los strings con relleno también cambian el hash y fuerzan reescrituras.
Recommendation: normalizar en el mapper (`strip()` + reemplazo de `\p{Cntrl}`) y, para logs, escapar CR/LF en un helper. Se resuelve junto con el registro estructurado que propone el Caso 8 del equipo.

[SECURITY] LOW
ID: SYS-SEC-07
Confidence: HIGH
Location: `SysacadSyncOrchestratorImpl.java:48-52`; `SysacadSyncController.java` (mapa de `SUCCESS` a 200)
Type: Diseño de API / observabilidad
Problem: si ya hay un resync en curso, `resyncAll()` devuelve `SUCCESS` y el controller responde 200 con el estado previo; el cliente no distingue "ejecutó" de "ignorado".
Impact: no hay vector de ataque; oculta fallos y complica la auditoría de quién disparó qué (el `@AuditOperation` no corre en `sync`, solo en eventos).
Recommendation: responder 409 o 202 en ese caso y auditar el disparo manual con usuario.

[SECURITY] LOW
ID: SYS-SEC-08
Confidence: MEDIUM
Location: `SysacadProperties.java:14-31`; DTOs `Raw*`; `Building/Classroom/Subject` sync (sin límites de longitud en `SysacadCatalogMapper`)
Type: Validación de entrada de API externa (API10:2023, ASVS V2.2)
Problem: (a) `baseUrl` y `apiKey` sin `@Validated`/`@NotBlank`; nada impide un `http://` por una variable de entorno equivocada. (b) Los DTOs `Raw*` aceptan cualquier longitud y rango (`capacidad` negativa, `inscriptos` negativo, `Dia`/hora sí se validan); la defensa es el `varchar` de la columna, que hace fallar la vista completa en vez de descartar la fila. No hay SSRF: el nombre de vista es constante en cada fetcher y `ViewQuery` no recibe input de usuario.
Impact: bajo; la clave viajaría en claro ante una URL mal configurada, y una fila con nombre largo aborta toda la vista (DoS parcial del sync, fail-safe).
Recommendation: `@Validated` con `@Pattern("https://.*")` en `baseUrl` y `@NotBlank` en `apiKey`; recortar o descartar filas con longitudes/rangos fuera de contrato en el mapper.

[SECURITY] INFO
ID: SYS-SEC-09
Confidence: LOW
Location: `.claude/docs/sysacad-sync-revision.md` (Cambios al esquema, tabla `sysacad_origen`)
Type: Recomendación sobre propuesta
Problem: la propuesta guarda el payload crudo por entidad.
Impact: crecimiento sin tope y, si algún día Sysacad incluye datos personales (docentes, inscriptos), copia sin control de acceso.
Recommendation: limitar tamaño por fila, exponerla solo a `PERM_SYSACAD_READ` y definir retención.

## Aspectos revisados sin hallazgo

- Autorización: POST con `hasAuthority('PERM_SYSACAD_SYNC')`, GET con `PERM_SYSACAD_READ` (`SysacadSyncController`); ambos globales, sin recurso por ID, así que no hay IDOR. El controller y todos los beans están bajo `@ConditionalOnProperty(siga.sysacad.enabled)`.
- Flags por perfil: `enabled` sin default en `application.yaml` (falla cerrado si falta); `dev-local` y `dev-snapshot` fijan `enabled: false` con una clave de relleno que no se usa; `application-integration.yaml` también en false. Clave real solo por entorno (`docker-compose.deploy.yml`, workflows de CD). No existen flags por vista: solo el `enabled` global y un cron por vista.
- Sin default inseguro de URL: `base-url` es https fijo en `application.yaml`; sin redirects (JDK `Redirect.NEVER` por defecto).
- Holiday: único sync de las tres vistas sin `requireRows` que tiene guarda propia (`HolidayServiceImpl.syncHolidays` lanza si no hay fechas válidas). ESPECIALIDADES y MATERIAS no dan de baja nada ausente (solo upsert), así que una vista vacía no destruye datos; no hay riesgo equivalente a SYS-SEC-01.
- Asignaciones: `AllocationWriter.syncFromSysacad` no pisa asignaciones manuales (`source != SYSACAD`) y no borra; una vista vacía no elimina asignaciones.

## Mapa de los casos del equipo (dimensión seguridad)

| Caso | Dónde ocurre hoy | Qué aporta seguridad |
|---|---|---|
| 1 Edificios duplicados | `BuildingServiceImpl.syncBuildings` (clave `buildingCode`, `Collectors.toMap` sin merge function en `existing` cae con IllegalStateException si hay códigos repetidos en base; en el feed repetido se sobreescribe) | Entra en SYS-SEC-01: `markAbsent` con feed degradado |
| 2 Aulas por edificio ausente | `ClassroomServiceImpl.syncClassrooms` (`building == null` -> `continue`) | El `continue` deja fuera de `incoming` esas aulas; ver SYS-SEC-01 |
| 5 Comisiones sin vínculo | `CommissionServiceImpl.syncCommissions` / `linkSubjectCommission` | SYS-SEC-01 y 02 |
| 6 Eventos/asignaciones | `AcademicEventSyncService.doSync`, `AllocationSyncService.doSync` | SYS-SEC-02 |
| 8 Logs insuficientes | `ViewSyncRunner`, mapper, servicios `sync*` | SYS-SEC-06 y 09: los logs más detallados deben sanear datos externos |
| 3, 4, 7 | fuera de la dimensión | sin hallazgo de seguridad |

El documento del equipo describe bien el flujo general; no menciona el riesgo de baja masiva por feed degradado, que es el punto de SYS-SEC-01/02.

## Cobertura ASVS 5.0.0 / OWASP

| Control | Estado | Referencia |
|---|---|---|
| V2 validación y lógica de negocio (datos externos) | FAIL | SYS-SEC-01, 02, 08 |
| API10:2023 Unsafe Consumption of APIs | FAIL | SYS-SEC-01, 02, 05, 08 |
| API4:2023 consumo de recursos | NEEDS_REVIEW | SYS-SEC-04, 05 |
| V8 autorización (permisos del endpoint) | PASS | `PERM_SYSACAD_SYNC` / `PERM_SYSACAD_READ`; confirmar alcance de READ |
| V13 / comunicación segura (TLS, redirects, timeouts de conexión) | PASS parcial | https fijo, verificación por defecto; conexión 5 s; ver SYS-SEC-05 |
| V13 gestión de secretos del servicio | PASS | clave por variable de entorno, sin default; placeholders en perfiles dev |
| V16 logging (inyección de logs) | FAIL | SYS-SEC-06 |
| V16 manejo de errores | FAIL | SYS-SEC-03 |
| Concurrencia / integridad de proceso | FAIL | SYS-SEC-04 |
| SSRF | NOT_APPLICABLE | URL y vista de config/constantes, sin input de usuario |
| A08:2025 integridad de datos | FAIL | SYS-SEC-01, 02 |
| Inyección SQL | NOT_APPLICABLE | el módulo no tiene queries propias; los repositorios usados no concatenan |
