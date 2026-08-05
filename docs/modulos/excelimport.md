# Módulo `excelimport`

## Responsabilidad

Carga masiva desde planilla Excel (Apache POI). Lee la plantilla oficial, valida su
formato y, fila por fila, **materializa** el árbol académico, el evento recurrente
(`findOrCreateRecurringEvent` de `events`) y su asignación de aula (`allocation`)
llamando a los `findOrCreate` de `academic`, `space`, `events` y `allocation`. Punto de
entrada de datos del sistema.

## API pública (`::api`)

Sin `::api` hacia otros módulos (es consumidor, no proveedor).

Endpoint REST:

| Método | Path | Descripción |
|---|---|---|
| POST | `/v1/excelimports` (multipart) | Sube el `.xlsx`, devuelve `ImportResultDto` |

`ImportResultDto`: `processedRows`, `assignmentsCreated`, `assignmentsReused`,
`entitiesCreated`, `entitiesReused` — contadores agregados de la corrida.

## Estructura interna

- **`ExcelTemplateValidator`** — valida el workbook, expone `validate(file)` y
  `extractYear(sheet)`.
- **`ExcelRowMapper`** — fila POI → `ExcelRowDto`.
- **`ImportCache`** — memoiza `findOrCreate` **dentro de una misma importación** para no
  repetir llamadas por entidades que se repiten entre filas.
- **`ExcelImportServiceImpl`** — orquestador (204 líneas). `@Transactional` sobre toda la
  importación (**all-or-nothing**). Recorre desde la fila índice 6 hasta `getLastRowNum()`,
  hoja fija `"Hoja1"`. Inyecta 11 servicios de 5 módulos.

## Dependencias

`academic::api`, `space::api`, `allocation::api`, `events::api`, `common`. Junto con
`allocation`, uno de los dos únicos módulos que agregan a varios otros.

## Gaps y oportunidades

- **Acoplamiento estructural a la plantilla.** Hoja hardcodeada `"Hoja1"`, datos desde la
  fila 6, año en celda fija. Cualquier cambio de layout rompe el import sin diagnóstico
  claro. Considerar constantes documentadas o config.
- **Todo-o-nada sin reporte por fila.** `@Transactional` global: una fila inválida al final
  revierte **toda** la carga. `ImportResultDto` no reporta **qué** filas fallaron ni por
  qué. Falta un modo que acumule errores por fila (`row, columna, motivo`) y/o import
  parcial.
- **Sin dry-run / validación previa.** No hay endpoint para validar la planilla y
  previsualizar qué se crearía antes de escribir. Útil para el usuario que sube el Excel.
- **POI carga el workbook completo en memoria.** Para planillas grandes conviene el modo
  streaming (SXSSF/eventos); hoy no es un problema pero es un límite conocido.
- **Nombre de archivo en logs vía `file.getName()`** (nombre del parámetro, no
  `getOriginalFilename()`) en el arranque del service — el log inicial no muestra el nombre
  real del archivo subido. Menor, cosmético.
- **Orquestador monolítico.** 204 líneas con 11 dependencias; extraer sub-pasos
  (resolución académica / resolución espacial / asignación) mejoraría testeo y lectura.

## Testing

**Estado actual: cero tests.**

### Unitarios recomendados
- `ExcelTemplateValidator`: workbook válido pasa; falta de `"Hoja1"`, encabezados o año
  ⇒ `ExcelFormatException`.
- `ExcelRowMapper`: celdas → `ExcelRowDto`, incluyendo tipos numéricos/texto y celdas vacías.
- `ImportCache`: el segundo `findOrCreate` del mismo key no vuelve a llamar al servicio.

### Integración (Testcontainers) recomendados
- Import end-to-end de un `.xlsx` de muestra (fixture en `src/test/resources`): verificar
  contadores de `ImportResultDto` y filas efectivamente creadas en cada módulo.
- **Idempotencia**: reimportar el mismo archivo ⇒ `*Reused` en vez de `*Created`, sin
  duplicados.
- **Rollback**: fila inválida ⇒ ninguna entidad persiste (transacción revertida).
</content>
