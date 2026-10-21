# Seguridad: ASVS 5.0.0, Top 10:2025, API Security Top 10:2023, RFC 8725

Vigencia: verificado el 2026-10-13.

## OWASP ASVS 5.0.0

Fuente: https://github.com/OWASP/ASVS/tree/master/5.0/en (presentado en mayo de 2025).
Unos 350 requisitos en 17 capítulos y tres niveles (L1, L2, L3). Citar con versión:
`v5.0.0-<capítulo>.<sección>.<requisito>`, por ejemplo `v5.0.0-8.2.1`.

| Capítulo | Aplica a este backend |
|---|---|
| V1 Encoding and Sanitization | Sí: SQL nativo/JPQL, logs (CRLF) |
| V2 Validation and Business Logic | Sí: Bean Validation, límites de listas, reglas de negocio |
| V3 Web Frontend Security | No (frontend) |
| V4 API and Web Service | Sí |
| V5 File Handling | Sí: imports Excel con Apache POI (tamaño, zip bomb, XXE) |
| V6 Authentication | Sí: login, rate limit, política de contraseñas |
| V7 Session Management | Sí: refresh tokens, revocación, reuso |
| V8 Authorization | Sí: `PERM_*`, alcance por edificio |
| V9 Self-contained Tokens | Sí: JJWT |
| V10 OAuth and OIDC | No (no hay OAuth) |
| V11 Cryptography | Sí: clave JWT, hashes |
| V12 Secure Communication | Parcial: TLS lo termina el despliegue |
| V13 Configuration | Sí: perfiles, Actuator, Swagger, secretos |
| V14 Data Protection | Sí: datos personales en logs y respuestas |
| V15 Secure Coding and Architecture | Sí: dependencias con CVE, fronteras |
| V16 Security Logging and Error Handling | Sí: qué se loguea, qué recibe el cliente |
| V17 WebRTC | No |

Nivel usado en 2026-10-13: L2.

## OWASP Top 10:2025

Fuente: https://owasp.org/Top10/2025/

A01 Broken Access Control (incluye SSRF), A02 Security Misconfiguration, A03 Software Supply
Chain Failures, A04 Cryptographic Failures, A05 Injection, A06 Insecure Design, A07
Authentication Failures, A08 Software or Data Integrity Failures, A09 Security Logging and
Alerting Failures, A10 Mishandling of Exceptional Conditions.

## OWASP API Security Top 10:2023

Fuente: https://owasp.org/API-Security/editions/2023/en/0x11-t10/

| ID | Riesgo | Qué mirar en SIGA |
|---|---|---|
| API1 | Broken Object Level Authorization (BOLA/IDOR) | Cada endpoint que recibe un ID aplica el alcance por edificio (`BuildingScopeResolver.requireAccess`) |
| API2 | Broken Authentication | Login, refresh, rate limit |
| API3 | Broken Object Property Level Authorization | Mass assignment: entidades JPA como `@RequestBody`, campos que el rol no debería cambiar |
| API4 | Unrestricted Resource Consumption | `@Size` en listas, page size máximo, uploads, solver |
| API5 | Broken Function Level Authorization | `@PreAuthorize` con `PERM_*` en cada endpoint (`SecurityAnnotationsArchTest`) |
| API6 | Unrestricted Access to Sensitive Business Flows | Endpoint público de solicitudes |
| API7 | Server Side Request Forgery | Llamadas salientes (cliente de Sysacad) con URL controlada por input |
| API8 | Security Misconfiguration | CORS, headers, Actuator, Swagger |
| API9 | Improper Inventory Management | Endpoints sin documentar, versiones viejas |
| API10 | Unsafe Consumption of APIs | Datos de Sysacad tratados como confiables |

## RFC 8725, JWT Best Current Practices (BCP 225)

Fuente: https://www.rfc-editor.org/rfc/rfc8725 (febrero de 2020, actualiza RFC 7519).
Secciones de la 3 que se citan: 3.1 Perform Algorithm Verification (algoritmo fijo, nunca el
del header ni `none`), 3.2 Use Appropriate Algorithms, 3.5 Ensure Cryptographic Keys Have
Sufficient Entropy, 3.8 Validate Issuer and Subject, 3.9 Use and Validate Audience, 3.10 Do Not
Trust Received Claims, 3.11 Use Explicit Typing, 3.12 Use Mutually Exclusive Validation Rules
for Different Kinds of JWTs.

## Lecciones del 2026-10-13

- CSRF deshabilitado es correcto con API stateless, JWT en `Authorization` y refresh token en el
  cuerpo JSON. Si alguna vez el refresh viaja en cookie, CSRF vuelve a aplicar.
- Detección de reuso de refresh token: si el método es `@Transactional` y revoca antes de
  lanzar una `RuntimeException`, el rollback deshace la revocación. Usar `noRollbackFor` o
  `REQUIRES_NEW`, con un test.
- Rate limit de login por email permite bloquear cuentas ajenas; por IP con `getRemoteAddr()`
  falla detrás de un proxy.
- Repo público: correr gitleaks sobre todo el historial (ver `tooling.md`); los JWT de las
  colecciones Bruno aparecen en commits viejos.
