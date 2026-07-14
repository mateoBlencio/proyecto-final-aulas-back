# ADR-001: Manejo de errores centralizado

## Estado

Aceptado — 2026-07-10

## Contexto

Cada excepción de dominio necesita traducirse a una respuesta HTTP
(`ProblemDetail`) con un status, un título y un detalle, y en algunos
casos con información adicional específica del error (por ejemplo, la
lista de conflictos de una reasignación).

Antes de esta decisión, `common/exception/GlobalExceptionHandler`
concentraba la traducción de las excepciones genéricas del proyecto
(`SigaAppException`, `MethodArgumentNotValidException`), pero el módulo
`allocation` definía su propio `@RestControllerAdvice`
(`ReassignConflictExceptionHandler`) con el único propósito de adjuntar
la propiedad `conflicts` al `ProblemDetail` de `ReassignConflictException`.
Esto generaba dos problemas:

- Múltiples `@RestControllerAdvice` compitiendo por el mismo tipo de
  responsabilidad (traducir excepciones a HTTP), lo que dificulta saber
  dónde buscar el manejo de un error dado y abre la puerta a que cada
  módulo nuevo agregue el suyo.
- Faltaban handlers para excepciones comunes de Spring/Jakarta
  (`ConstraintViolationException`, `HttpMessageNotReadableException`,
  `MethodArgumentTypeMismatchException`, `MaxUploadSizeExceededException`)
  y no existía un catch-all para `Exception` que evitara filtrar el
  mensaje interno (stack trace, mensajes de librerías, etc.) hacia el
  cliente ante un error no controlado.

## Decisión

Toda la traducción de excepción a HTTP se centraliza en un único
`@RestControllerAdvice`: `common/exception/GlobalExceptionHandler`. Los
módulos de negocio no definen sus propios advices.

- `SigaAppException` es la jerarquía base de la que heredan las
  excepciones de dominio de cada módulo. Provee `status`, `title` y
  `detail` (mensaje de la excepción), y además un mecanismo extensible
  de propiedades (`withProperty(key, value)` / `getProperties()`) para
  que una subclase pueda adjuntar información adicional sin necesitar
  un handler propio. `GlobalExceptionHandler` copia automáticamente
  esas propiedades al `ProblemDetail` de respuesta.
- Los módulos aportan subclases de `SigaAppException` (por ejemplo
  `ReassignConflictException`, `SpaceDomainException`), nunca advices
  propios. El advice de `allocation` (`ReassignConflictExceptionHandler`)
  se elimina: `ReassignConflictException` ahora adjunta `conflicts` vía
  `withProperty` en su constructor y `GlobalExceptionHandler` lo expone
  automáticamente.
- `GlobalExceptionHandler` también maneja excepciones estándar de
  validación y de framework (`ConstraintViolationException`,
  `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`,
  `MaxUploadSizeExceededException`) devolviendo mensajes en español sin
  filtrar detalles internos.
- Se agrega un catch-all para `Exception` que responde siempre con
  detail fijo `"Error interno del servidor."` (nunca el mensaje de la
  excepción original) y registra el stack trace completo con
  `log.error`, evitando exponer información sensible o de
  implementación al cliente.

## Consecuencias

- Un único punto de entrada para entender cómo se traduce cualquier
  excepción a HTTP en todo el proyecto.
- Las excepciones de dominio nuevas solo necesitan extender
  `SigaAppException` y, si corresponde, usar `withProperty` en su
  constructor; no requieren tocar `GlobalExceptionHandler` salvo que la
  propiedad deba tener un tratamiento especial.
- Se reduce el riesgo de fuga de información interna ante errores no
  controlados, ya que el catch-all nunca devuelve el mensaje original
  de la excepción.
- Todo el equipo debe respetar la convención de no crear
  `@RestControllerAdvice` en módulos de negocio; si aparece uno nuevo,
  debe tratarse como una desviación a corregir.

## Alternativas consideradas

- **Mantener un advice por módulo para casos especiales**: se descartó
  porque dispersa la lógica de traducción a HTTP y dificulta tener una
  visión completa de los formatos de error que expone la API.
- **Resolver `conflicts` con un handler específico por excepción en
  `common`**: se descartó a favor de un mecanismo genérico de
  propiedades (`withProperty`/`getProperties`) en `SigaAppException`,
  que sirve para cualquier excepción futura sin agregar un
  `@ExceptionHandler` nuevo por cada caso.
