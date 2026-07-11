# Módulo `common`

## Responsabilidad

Módulo **OPEN** (`@ApplicationModule(type = OPEN)`): infraestructura transversal que
cualquier módulo puede usar sin declararlo en `allowedDependencies`. No tiene lógica de
dominio ni entidades de negocio.

## Contenido

| Paquete | Pieza | Rol |
|---|---|---|
| `exception` | `GlobalExceptionHandler` | `@RestControllerAdvice`, mapea excepciones → `ProblemDetail` (RFC 7807). ADR-001 |
| `exception` | `SigaAppException`, `ResourceNotFoundException`, `InvalidDateRangeException` | Jerarquía base de errores de negocio |
| `dto` | `FindOrCreateResult<T>` | Resultado uniforme "buscar o crear" (`value` + `created`); helpers `resolve` / `map` |
| `converter` | `DurationMinutesConverter` | `@Converter(autoApply)` `Duration ↔ Integer` (minutos) |
| `audit` | `SigaRevision` | `@RevisionEntity` de Envers con timestamp `LocalDateTime` en `revinfo`. ADR-007 |
| `mapper` | `CentralMapperConfig` | Config MapStruct compartida. ADR-002 |
| `config` | `CorsConfig`, `OpenApiConfig` | CORS + Swagger |

### `GlobalExceptionHandler` — mapeo

Un único `@ExceptionHandler(SigaAppException.class)` traduce **cualquier** subtipo: el
status y el título los lleva la propia excepción (`SigaAppException.status`/`title`,
seteados en el constructor de cada subtipo), no hay un handler por clase. Subtipos
existentes (uno por módulo de dominio) y su status:

| Excepción | Módulo | HTTP |
|---|---|---|
| `ResourceNotFoundException` | `common` | 404 |
| `SpaceDomainException` | `space` | 400 |
| `InvalidDateRangeException` | `common` | 400 |
| `ExcelFormatException` | `excelimport` | 400 |
| `AllocationConflictException` | `allocation` | 409 |
| `ReassignConflictException` | `allocation` | 409 (lleva `conflicts: List<OccurrenceConflictDto>` como propiedad extra del `ProblemDetail`) |
| `ExcelImportException` | `excelimport` | 422 |
| `ExpiredPreviewException` | `solver` | 410 |
| `SchedulingException` | `solver` | 500 |

Excepciones de framework, cada una con su propio `@ExceptionHandler`:

| Excepción | HTTP |
|---|---|
| `MethodArgumentNotValidException` | 400 |
| `ConstraintViolationException` | 400 |
| `HttpMessageNotReadableException` | 400 |
| `MethodArgumentTypeMismatchException` | 400 |
| `MaxUploadSizeExceededException` | 413 |
| `Exception` (catch-all) — si es `ErrorResponse` (404/405 de Spring, etc.) conserva su `ProblemDetail` original; si no, 500 genérico | 500 (o el status del `ErrorResponse` original) |

Verificado contra el código: no hay excepciones de negocio nuevas fuera de esta lista
(sprint 03 agregó `ReassignConflictException` y `ExpiredPreviewException`, ambas ya
reflejadas arriba).

## Dependencias

Ninguna (base del grafo). Todos dependen de `common`.

## Gaps y oportunidades

- **Módulo OPEN = frontera no vigilada.** Por diseño (config compartida), pero significa
  que cualquier clase que caiga acá queda accesible globalmente. Vigilar que no se filtre
  lógica de dominio a `common` para no convertirlo en cajón de sastre.
- **`SigaRevision` sin usuario.** Registra `rev` + `fecha_revision` pero **no quién** hizo
  el cambio. Cuando exista autenticación, agregar `usuario` al `@RevisionEntity`
  (`RevisionListener`) para cerrar la trazabilidad que promete ADR-007.
- **Catch-all `Exception → 500`**: correcto como red final, pero conviene asegurar que no
  se filtren detalles internos en el `ProblemDetail` (mensaje genérico en prod).

## Testing

**Estado actual: cero tests.**

### Unitarios recomendados
- `DurationMinutesConverter`: round-trip `Duration ↔ Integer`, y manejo de `null` en ambas
  direcciones.
- `FindOrCreateResult.resolve`: rama existe (`created=false`, no invoca `creator`) vs no
  existe (`created=true`, invoca `creator` una vez); `map` preserva el flag.

### Integración recomendados (`@WebMvcTest` / slice)
- `GlobalExceptionHandler`: cada excepción mapeada devuelve el status y el `ProblemDetail`
  esperado (body válido, tipo de contenido `application/problem+json`).
- `SigaRevision`: al tocar una entidad `@Audited` se crea una fila en `revinfo` con
  timestamp `LocalDateTime` legible (junto con los tests de Envers de `allocation`).
</content>
