# API y UX de errores: RFC 9457, Spring, Zalando, Nielsen

Vigencia: verificado el 2026-10-13.

## RFC 9457, Problem Details for HTTP APIs

Fuente: https://www.rfc-editor.org/rfc/rfc9457 (reemplaza a RFC 7807 sin romper compatibilidad).
Miembros: `type`, `title`, `status`, `detail`, `instance`, más extensiones propias. Media type
`application/problem+json`.

En Spring (https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html):
`ProblemDetail`, `ErrorResponse`, `ErrorResponseException`, y un `@ControllerAdvice` que
extiende `ResponseEntityExceptionHandler` para que las excepciones propias de Spring MVC
también salgan como Problem Details.

Chequeos:
- Un único formato para todos los errores, incluidos 401/403/429 de los filtros de seguridad.
- `detail` legible para el usuario final, sin mensajes internos (`cause.getMessage()`), SQL,
  nombres de clase ni stack traces.
- Excepciones sin handler que terminan en 500: `PropertyReferenceException` (sort inválido),
  `DataIntegrityViolationException` (carrera de duplicados, debería ser 409),
  `OptimisticLockingFailureException` (409).
- Códigos: 400 validación, 404 inexistente, 409 conflicto o duplicado, 422 regla de negocio si
  el equipo lo adopta, 429 con `Retry-After`.

## Zalando RESTful API Guidelines

Fuente: https://opensource.zalando.com/restful-api-guidelines/

Reglas que se usaron como criterio (el número de regla cambia entre versiones; citar por nombre):
- Problem JSON para errores.
- Paths en minúscula con guiones (kebab-case) y recursos en plural.
- Códigos HTTP estándar y su semántica; 201 con `Location` al crear.
- Paginación en toda colección que crece, con tamaño máximo.
- Formato de fecha y hora RFC 3339 con zona.

Fuera de Zalando: un `DELETE` con cuerpo es frágil porque RFC 9110 (sección 9.3.5) dice que su
contenido no tiene semántica definida y algunos proxies lo descartan.

## Heurísticas de Nielsen

Fuente: https://www.nngroup.com/articles/ten-usability-heuristics/ (Molich y Nielsen 1990,
estables desde 1994).

1 Visibility of system status, 2 Match between system and the real world, 3 User control and
freedom, 4 Consistency and standards, 5 Error prevention, 6 Recognition rather than recall,
7 Flexibility and efficiency of use, 8 Aesthetic and minimalist design, 9 Help users
recognize, diagnose, and recover from errors, 10 Help and documentation.

En el backend aplican sobre todo la 5 y la 9:
- 5: rechazar temprano (validación antes del trabajo costoso), topes que eviten el error.
- 9: mensajes en español que digan qué campo falló y cómo corregirlo, sin códigos ni jerga
  ("AcademicPeriod not found with id: 5" incumple). Fijar el locale de los mensajes de Bean
  Validation para que no dependan de `Accept-Language`.

## OpenAPI (springdoc)

Esquema de seguridad declarado y aplicado (si no, el botón Authorize de Swagger no manda el
token), `@Tag`/`@Operation` por controller, respuestas de error documentadas.
