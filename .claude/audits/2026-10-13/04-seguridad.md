# Dimensión 4: Seguridad (ASVS 5.0.0 L2, API Top 10:2023)

Base: commit 22c693f2. Solo lectura, sin sysacad. No se corrió mvnw ni SpotBugs; los avisos de SpotBugs/FindSecBugs se tomaron de `03-herramientas.md` y se verificaron en código. Ningún valor secreto aparece en este archivo.

Resultado de la revisión de BOLA/IDOR (prioridad máxima): el alcance por edificio se aplica en todos los endpoints con ID que lo necesitan (aulas vía `ScopedClassroomFinder` con 404 fuera de alcance, asignaciones, dashboard, edificios, preview, pedidos de aula vía `RoomRequestAccessControl`). Todos los controllers excepto los públicos declarados llevan `@PreAuthorize`; `SecurityAnnotationsArchTest` lo fija. No hay SQL concatenado con input externo (los `+` en `AuditArchive*Repository` usan nombres de tabla de un enum). No hay entidades JPA como `@RequestBody` (mass assignment cerrado, todo son records). El único endpoint de escritura público es `POST /v1/room-requests`, intencional.

## Hallazgos

### SEC-01 Rate limit de login por email: bloqueo de cuentas ajenas y evicción
- Severidad: Media
- Esfuerzo: S
- Referencia: ASVS v5.0.0-6.3.1, API4:2023
- Evidencia: auth/security/LoginRateLimiter.java:15-43 (clave = email, `maximumSize(10_000)`, en memoria); auth/service/impl/AuthServiceImpl.java:55-70
- Verificación: Confirmado (bloqueo); Plausible (evicción)
- Problema: Cualquiera que conozca un email institucional (por ejemplo el de la Subsecretaría) lo bloquea 15 minutos con 5 logins fallidos, repetible. Además, 10.000 fallos con emails distintos pueden expulsar el registro de una víctima de la caché y reiniciar su contador. `/auth/refresh` no tiene límite propio (el token de 256 bits lo hace inviable de adivinar, no de saturar).
- Arreglo: Clave compuesta email+IP (o contador por IP además del de email) y bloqueo progresivo en vez de duro. El límite por IP debe leer la IP real (ver SEC-02).

### SEC-02 Rate limit general por `getRemoteAddr()` detrás de proxy
- Severidad: Media
- Esfuerzo: S
- Referencia: ASVS v5.0.0-4.1.x / 13.x (config detrás de proxy), API4:2023
- Evidencia: common/web/RateLimitFilter.java:56 (`request.getRemoteAddr()`); application.yaml `server.forward-headers-strategy: framework`; no hay `server.tomcat.remoteip.*`; el filtro corre en `HIGHEST_PRECEDENCE+1` (common/config/RateLimitFilterConfig.java)
- Verificación: Plausible (el proxy inverso no está en el repo)
- Problema: Si el deploy pasa por un reverse proxy, todos los clientes comparten una IP y un único bucket de 100 req/min: un cliente ruidoso deja sin servicio al resto y el límite por IP no distingue atacantes. `forward-headers-strategy: framework` no cambia `getRemoteAddr()`. El mismo valor se loguea como IP en el login.
- Arreglo: Configurar `server.tomcat.remoteip.internal-proxies` + `remote-ip-header=X-Forwarded-For` con la IP del proxy, o `forward-headers-strategy: native`. Confirmar la topología con quien despliega.

### SEC-03 Sin política de contraseñas y sin endpoint de cambio
- Severidad: Media
- Esfuerzo: S (validación) / M (cambio de contraseña)
- Referencia: ASVS v5.0.0-6.2.1, 6.2.2, 6.2.9
- Evidencia: auth/dto/request/CreateUserRequestDto.java (`@NotBlank String password`); no existe endpoint de cambio de contraseña en AuthController/UserController
- Verificación: Confirmado
- Problema: Se acepta cualquier contraseña no vacía (1 carácter) y el usuario no puede cambiar la que le fijó la Subsecretaría. Sin tope superior, BCrypt trunca a 72 bytes (o lanza excepción según la versión de Spring Security, 500 en ese caso).
- Arreglo: `@Size(min=8, max=64)` en `CreateUserRequestDto.password` ya. Endpoint de cambio de contraseña propio como deuda.

### SEC-04 Datos personales y entrada sin neutralizar en logs
- Severidad: Baja
- Esfuerzo: S
- Referencia: ASVS v5.0.0-16.4.1, 14.x; SpotBugs CRLF_INJECTION_LOGS (281 avisos, ver 03-herramientas.md)
- Evidencia: auth/service/impl/AuthServiceImpl.java:47,59,68,70 (email + IP en cada login); roomrequest/controller/RoomRequestController.java:69 y roomrequest/service/impl/RoomRequestServiceImpl.java:50 (teacherName en DEBUG, endpoint público); application-dev.yaml/application-test.yaml (`ar.edu.utn.frc.siga: DEBUG`); preview/controller/PreviewController.java (`previewId` String de la URL logueado)
- Verificación: Confirmado (PII); Plausible (inyección CRLF explotable)
- Problema: Los logs de login traen email e IP (necesarios para investigar, pero son dato personal) y dev/test corren en DEBUG con nombres de docentes cargados desde un endpoint anónimo. El email del login pasa por `@Email`, que no admite CR/LF, así que el riesgo real de forja de líneas está en los valores String de path/query (`previewId`, `suggestionId`) que llegan a `log.*` sin filtrar.
- Arreglo: Reemplazar `\r\n` en el patrón de logback (`%replace(%msg){'[\r\n]',' '}`) o encoder JSON; quitar `teacherName` de los logs DEBUG; fijar retención de logs.

### SEC-05 Entradas sin tope en la solicitud pública de aulas
- Severidad: Media
- Esfuerzo: S
- Referencia: ASVS v5.0.0-2.2.1, API4:2023
- Evidencia: roomrequest/dto/request/CreateOtherDto.java (`@NotEmpty List<FreeFormItemDto> items` sin `@Size(max)`); ScheduledItemDto.java/FreeFormItemDto.java (`preferredClassroomIds` sin tope); config: solo el rate limit global por IP; `POST /v1/room-requests` es `permitAll` (SecurityConfig.java:56)
- Verificación: Confirmado (falta el tope); Plausible (impacto)
- Problema: Un anónimo puede mandar una solicitud con miles de ítems o de aulas preferidas (cada ítem dispara validaciones y escrituras) y llenar la bandeja de la Subsecretaría con spam. Sin CAPTCHA ni verificación del email del docente, `teacherEmail` es arbitrario (el correo de notificación saldría a un tercero cuando se notifique).
- Arreglo: `@Size(max=50)` en `items` y `preferredClassroomIds`, `@Size` en `items` de todos los `Create*Dto`, y un límite de solicitudes por IP más estricto para ese endpoint.

### SEC-06 Reasignar una ocurrencia solo valida el aula destino
- Severidad: Media
- Esfuerzo: S
- Referencia: ASVS v5.0.0-8.2.2, API1:2023 / API5:2023
- Evidencia: allocation/service/impl/AllocationServiceImpl.java:166-170 (`resolveAndValidate` -> `requireAccessToClassrooms` solo con las aulas destino); contraste con `deallocate` (líneas 126, 205-214) que sí valida las aulas actuales
- Verificación: Plausible (no se leyó `AllocationWriter.upsert`)
- Problema: Un auxiliar del edificio A puede reasignar a un aula de A una ocurrencia hoy asignada en el edificio B, quitándole el aula a B. `deallocate` bloquea ese caso, `reallocate` no.
- Arreglo: Llamar `requireAccessToCurrentClassrooms(occurrences)` también en `reallocate` (ver pregunta 2).

### SEC-07 JWT sin issuer/audience/jti
- Severidad: Baja
- Esfuerzo: S
- Referencia: ASVS v5.0.0-9.2.1, 9.2.3; RFC 8725 §3.8-3.9
- Evidencia: auth/security/JwtService.java:25-33 (solo sub, iat, exp), 41-46 (parser sin `requireIssuer`/`requireAudience`)
- Verificación: Confirmado
- Problema: El token no es rechazable por emisor ni destinatario. Positivos: algoritmo fijado por la clave HMAC (alg=none rechazado por `parseSignedClaims`), exp validado, clave de al menos 32 bytes forzada en `JwtProperties.validate`, y el usuario se recarga en cada request, de modo que deshabilitar o cambiar roles rige de inmediato. El access token no se revoca en logout (20 min).
- Arreglo: Agregar `issuer("siga")` al generar y `requireIssuer("siga")` al parsear. Sin urgencia mientras exista un solo servicio.

### SEC-08 Tokens de acceso y refresh en el historial público
- Severidad: Baja
- Esfuerzo: S
- Referencia: ASVS v5.0.0-13.3.x (secretos), 7.4.x; gitleaks (03-herramientas.md)
- Evidencia: bruno/auth/Refresh.bru y bruno/auth/Logout.bru en commits 862b8f91 y 89e42a9f; JWT de acceso (SUBSECRETARIA, exp 20 min) en 13 commits de bruno/*.bru. Consulta en dev-snapshot: `select count(*) from refresh_token where token_hash in (<hash SHA-256 base64url de los 2 tokens>)` devuelve 0
- Verificación: Confirmado
- Problema: Los JWT están vencidos. Los dos refresh tokens (30 días, hash SHA-256 en tabla `refresh_token`, rotados en cada uso) no figuran en la base de dev-snapshot, por lo que no se pueden dar por muertos con certeza solo en ese entorno: dev y test reales no se consultaron. Reuso de un token rotado revoca en cascada, lo que limita el uso.
- Arreglo: Confirmar en las bases dev y test que esos hashes no existen o están revocados (`revocado = true`); no hace falta reescribir el historial. Agregar gitleaks como hook pre-commit o paso de CI y mantener `bruno/.env` ignorado (ya lo está).

### SEC-09 IP de Tailscale y secreto JWT de perfiles locales en el repo
- Severidad: Baja
- Esfuerzo: S
- Referencia: ASVS v5.0.0-13.3.1, 13.4.x
- Evidencia: bruno/environments/dev.bru y test.bru (`baseUrl` con IP 100.x de la red privada); application-dev-local.yaml:51 y application-dev-snapshot.yaml:34 (`siga.jwt.secret` en claro, valor idéntico en ambos; no coincide con el de JwtServiceTest.java:14 ni con .env.example, que lo deja vacío)
- Verificación: Confirmado
- Problema: La IP interna rompe la regla del equipo de no publicar IPs en el repo público. El secreto de dev-local/dev-snapshot es público: si algún deploy real (dev/test/prod) lo usa o lo usó, cualquiera forja tokens. El deploy compartido lee `JWT_SECRET_DEV/TEST` de secrets, y el valor de test unitario es distinto.
- Arreglo: Pasar `baseUrl` a variable de `bruno/.env`; mover el secreto de los perfiles locales a variable con default de desarrollo, y rotar `JWT_SECRET_DEV/TEST` si alguna vez coincidieron (ver pregunta 1).

### SEC-10 Swagger y detalle de health abiertos en dev/test
- Severidad: Baja
- Esfuerzo: S
- Referencia: ASVS v5.0.0-13.4.x, 16.5.1
- Evidencia: application-dev.yaml y application-test.yaml (`springdoc` habilitado, `health.show-details: always`); SecurityConfig.java:54-55 (`/actuator/health/**` y `/swagger-ui/**`, `/v3/api-docs/**` en `permitAll`)
- Verificación: Confirmado
- Problema: En dev y test cualquiera con acceso de red lee el esquema completo de la API y el detalle del health (estado de BD, disco, mail). Prod queda correcto (`show-details: never`, springdoc apagado, actuator solo expone `health`).
- Arreglo: `show-details: when-authorized` en dev/test; mantener Swagger solo si esos entornos están restringidos a la red privada.

### SEC-11 Cuerpo de importación Excel y solver sin tope de recursos
- Severidad: Baja
- Esfuerzo: M
- Referencia: ASVS v5.0.0-5.2.1, 5.2.3; API4:2023
- Evidencia: ingest/validator/ExcelTemplateValidator.java:54-55 (`WorkbookFactory.create(is)`, carga completa en memoria, extensión por nombre de archivo); preview/dto/request/PreviewRequestDto.java (`timeLimitSeconds` hasta 300); no hay semáforo de concurrencia en preview/ ni en CaffeinePreviewStore (sin `maximumSize`)
- Verificación: Plausible
- Problema: POI 5.4.0 trae protección por defecto contra zip bombs (`ZipSecureFile`) y XXE, y el multipart se limita a 10 MB; aun así un xlsx de 10 MB se expande en el heap de un contenedor de 2 GB. Un usuario con `PREVIEW_RUN` puede lanzar varios solvers de 300 s en paralelo. Ambos requieren sesión con rol (INGEST_RUN solo SUBSECRETARIA).
- Arreglo: Validar el tamaño de hoja/filas antes de leer, y limitar solvers concurrentes (semáforo) y entradas del store.

### SEC-12 Refresh tokens vencidos no se purgan
- Severidad: Baja
- Esfuerzo: S
- Referencia: ASVS v5.0.0-7.x, 14.2.7 (retención)
- Evidencia: auth/repository/RefreshTokenRepository.java (sin método de borrado de vencidos), sin scheduler asociado; dev-snapshot: `select count(*) from refresh_token` = 507 (36 vigentes)
- Verificación: Confirmado
- Problema: La tabla crece sin límite con hashes de sesiones que ya no sirven.
- Arreglo: Job semanal `delete where fecha_expiracion < now() - interval '30 days'`.

### SEC-13 MailHog publicado en el host de test
- Severidad: Baja
- Esfuerzo: S
- Referencia: ASVS v5.0.0-13.4.x
- Evidencia: docker-compose.deploy.yml (servicio `mailhog-test`, puertos 8026 y 1026 publicados, sin autenticación)
- Verificación: Confirmado
- Problema: Cualquiera con acceso al host lee los correos de prueba (nombres y emails de docentes reales si se cargan datos).
- Arreglo: Publicar solo en 127.0.0.1 o quitar los puertos y acceder por la red docker.

### SEC-14 La revocación por reuso de refresh token se deshace por rollback (ref. QA-B-01)
- Severidad: Alta
- Esfuerzo: S
- Referencia: ASVS v5.0.0-7.4.x, 9.x (revocación); RFC 9700 §4.14 (detección de reuso); QA-B-01 (diff-reviewer)
- Evidencia: auth/service/impl/RefreshTokenServiceImpl.java:58-74,104-111 (`refresh` es `@Transactional`; `handleReuse` y el caso de usuario deshabilitado llaman `revokeAllByUserId` y luego lanzan `InvalidRefreshTokenException`); common/exception/SigaAppException.java:11 (extiende RuntimeException); `rg noRollbackFor` sin resultados; AuthServiceImpl.refresh también es `@Transactional` y propaga
- Verificación: Confirmado (lectura de código; no se reprodujo con un test)
- Problema: La excepción es unchecked y no hay `noRollbackFor`, así que la transacción se marca rollback-only y el `UPDATE` de revocación en cascada no se persiste. Quien robó un refresh token y lo usa después de la rotación de la víctima no provoca la invalidación de la cadena, que es la defensa que el diseño promete. Lo mismo ocurre al deshabilitar al usuario (la revocación también se pierde, aunque el filtro JWT y el rechazo por `enabled` lo cubren).
- Arreglo: `@Transactional(noRollbackFor = InvalidRefreshTokenException.class)` en `RefreshTokenServiceImpl.refresh` y en `AuthServiceImpl.refresh`, más un test de integración que verifique que tras el reuso los hijos quedan revocados.

## Controles revisados sin hallazgo
- CSRF: `csrf.disable()` es correcto. No hay cookies en el código (`rg -i cookie` sin resultados); el refresh token viaja en el cuerpo JSON y el access token en `Authorization`. Confirma el punto SPRING_CSRF_PROTECTION_DISABLED de 03-herramientas.md como falso positivo.
- CORS: los orígenes salen de `CORS_ALLOWED_ORIGINS`, vacío por defecto, sin comodín y con `allowCredentials(false)`.
- Autenticación: BCrypt, sesión `STATELESS`, mensaje único de credenciales inválidas, dominio institucional validado, refresh con rotación, detección de reuso y revocación en cascada; refresh con 256 bits aleatorios (`SecureRandom`), guardado como SHA-256.
- Errores: `GlobalExceptionHandler` devuelve ProblemDetail sin traza; 500 con mensaje genérico.
- Inyección: JPQL/Specifications parametrizados; SQL nativo sin input externo; CSV de auditoría con prefijo anti formula-injection; plantillas Thymeleaf sin `utext`.
- Dependencias: no se revisaron aquí; los CVE de Spring Boot/Tomcat/Jackson los consolida el hilo principal como hallazgo de supply chain.

## Resumen
- 14 hallazgos: 0 Críticos, 1 Alto (SEC-14), 5 Medios (SEC-01, 02, 03, 05, 06), 8 Bajos.
- BOLA/IDOR bien resuelto; la única duda es el origen de la reasignación (SEC-06).
- Lo más urgente: SEC-14 (una línea de anotación restaura la revocación en cascada). Después SEC-03 (`@Size` en contraseña), SEC-05 (`@Size` en listas públicas), SEC-01/02 (clave de rate limit y IP real).
- Los refresh tokens del historial no existen en dev-snapshot; falta confirmarlo en dev y test (SEC-08).
- CSRF y CRLF confirmados: CSRF no aplica (sin cookies); CRLF es teórico para el email y plausible para path variables (SEC-04).

## Preguntas para el usuario
1. El secreto JWT de application-dev-local.yaml/dev-snapshot.yaml, ¿se usó alguna vez como `JWT_SECRET_DEV/TEST`? Si sí, SEC-09 sube a Alta y hay que rotar.
2. ¿Es regla de negocio que un auxiliar de un edificio pueda mover ocurrencias de otro edificio hacia el suyo con `PUT /v1/allocations`? Define si SEC-06 es hallazgo.
3. ¿El backend de dev/test/prod está detrás de un reverse proxy? Define SEC-02.
4. ¿Los entornos dev/test son accesibles solo por Tailscale? Define si SEC-10 y SEC-13 bajan a Informativo.

## Cobertura ASVS 5.0.0

| Capítulo | Estado | Hallazgos |
|---|---|---|
| V1 Encoding and Sanitization | Revisado | SEC-04 |
| V2 Validation and Business Logic | Revisado | SEC-05, SEC-06 |
| V3 Web Frontend Security | No aplica (API) | |
| V4 API and Web Service | Revisado | SEC-02, SEC-10 |
| V5 File Handling | Revisado (POI) | SEC-11 |
| V6 Authentication | Revisado | SEC-01, SEC-03 |
| V7 Session Management | Revisado | SEC-08, SEC-12, SEC-14 |
| V8 Authorization | Revisado (BOLA/RBAC por edificio) | SEC-06 |
| V9 Self-contained Tokens | Revisado | SEC-07, SEC-14 |
| V10 OAuth and OIDC | No aplica | |
| V11 Cryptography | Revisado (BCrypt, SHA-256, SecureRandom, HS256) | sin hallazgo |
| V12 Secure Communication | No revisado (TLS lo termina el proxy, fuera del repo) | |
| V13 Configuration | Revisado | SEC-09, SEC-10, SEC-13 |
| V14 Data Protection | Revisado | SEC-04 |
| V15 Secure Coding and Architecture | Revisado | sin hallazgo |
| V16 Security Logging and Error Handling | Revisado | SEC-04 |
| V17 WebRTC | No aplica | |
