# ADR-009: Importación Excel — acceso a datos en batch y persistence context acotado

## Estado

Aceptado — 2026-07-13

## Contexto

La importación masiva desde Excel (`ExcelImportServiceImpl.importExcel`) procesa
cientos de filas en **una única transacción** (semántica all-or-nothing, que se
conserva). Por cada fila resuelve/crea entidades académicas, un evento recurrente
y la asignación de aula a todas sus occurrences (~16-18 por cuatrimestre). Con
planillas reales el import tardaba minutos, y cada fila era más lenta que la
anterior. Las causas, en orden de impacto:

1. **N+1 con auto-flush cuadrático**: `AllocationServiceImpl.allocateToOccurrences`
   consultaba la allocation existente de cada occurrence **de a una**
   (`findByOccurrence_Id`, ~17 queries por fila). Cada query dispara el auto-flush
   de Hibernate, que dirty-checkea **todo** el persistence context — y este crecía
   fila a fila (eventos, occurrences y allocations, todos `@Audited` con Envers,
   ver ADR-007). ~500 filas × ~17 occurrences ≈ 8.500 queries, cada una recorriendo
   un contexto cada vez más grande → costo O(n²).
2. **Persistence context sin límite**: la transacción única acumulaba decenas de
   miles de entidades manejadas; cada flush se volvía progresivamente más caro.
3. **DTOs compuestos descartados**: `importAssignmentsFromDate` devolvía
   `List<AllocationResponseDto>` (cuya composición cuesta queries de materia,
   comisión y aula) y el import **ignoraba el retorno**. Ídem
   `findOrCreateRecurringEvent`, que componía el DTO completo del evento (2
   queries) cuando el caller solo usaba el id y el flag `created()`. Ambos métodos
   tienen como único caller productivo al módulo `excelimport`.
4. **Sin batching JDBC**: `application.yaml` no configuraba
   `hibernate.jdbc.batch_size`; cada UPDATE/INSERT viajaba individualmente a
   Postgres.

## Decisión

Se aplican cuatro medidas complementarias (commit `abd134d`):

- **Prefetch en batch de allocations existentes**: `allocateToOccurrences` carga
  en una sola query (`AllocationRepository.findByOccurrence_IdIn`, que ya existía)
  las allocations de todas las occurrences del evento y resuelve el upsert contra
  un mapa en memoria. El patrón general ya era regla del proyecto (variantes
  `findByIds` en las fachadas, composers por lote — ADR-002); esta decisión lo
  extiende al camino de escritura del import.
- **Retornos lean en los intent methods exclusivos del import**:
  `AllocationService.importAssignmentsFromDate` devuelve `int` (cantidad
  aplicada) y `AcademicEventService.findOrCreateRecurringEvent` devuelve
  `FindOrCreateResult<Long>` (id del evento). La regla que queda establecida:
  **un método de fachada no compone DTOs que su caller no consume**; si el único
  consumidor necesita solo el id, la fachada devuelve el id. Los paths con
  consumidor HTTP real (`assignManuallyFromDate`, `createRecurringEvent`) siguen
  devolviendo el DTO compuesto.
- **Persistence context acotado**: `ExcelImportServiceImpl` inyecta
  `EntityManager` y ejecuta `flush()` + `clear()` cada 50 filas, dentro de la
  misma transacción. El dirty-check de cada flush queda acotado a ~50 filas de
  entidades en lugar de crecer sin límite. Es seguro porque el `ImportCache` del
  import guarda DTOs (records), nunca entidades, y cada fila re-resuelve por
  repositorio lo que necesita — consecuencia directa de las fronteras por DTO de
  ADR-004.
- **Batching JDBC global**: `spring.jpa.properties.hibernate.jdbc.batch_size: 50`
  con `order_inserts` y `order_updates` en `application.yaml` (base, todos los
  perfiles). Agrupa en lotes los UPDATE (estados de occurrence) y los INSERT de
  las tablas de auditoría `_aud` de Envers.

Además, el log por fila del loop de import baja de `info` a `debug`.

## Consecuencias

- El costo por fila pasa de ~17 queries con dirty-check cuadrático a ~1 query
  batcheada con contexto acotado; el import deja de degradarse a medida que
  avanza la planilla.
- Sin cambio de comportamiento observable: mismas entidades, allocations y
  respuesta HTTP (`ImportResultDto` intacto); la atomicidad de la transacción
  única se conserva (un fallo en cualquier fila revierte todo). Verificado por la
  suite completa (334 tests, incluidos los de integración con Testcontainers).
- El contrato de las dos fachadas cambió para su único caller; si a futuro un
  controller necesita exponer esos flujos con DTO completo, deberá componerlo en
  el módulo dueño (patrón composer, ADR-002) o agregar una variante que componga.
- `clear()` periódico introduce una regla local al loop de import: ninguna
  referencia a entidades JPA puede sobrevivir entre chunks de 50 filas. Hoy se
  cumple por construcción (solo DTOs en el cache); si alguien introduce una
  entidad retenida entre filas, fallará con entidades detached.
- Los INSERT de entidades siguen sin batchear porque las PKs son `IDENTITY`
  (limitación de Hibernate); ver alternativas.

## Alternativas consideradas

- **Cambiar PKs `IDENTITY` → `SEQUENCE` para batchear también los INSERT**: se
  descartó porque el esquema lo administra un DBA externo (no hay migraciones,
  `ddl-auto: validate`) y exigiría un ciclo DDL completo para una mejora menor
  frente a las ya aplicadas. Puede reevaluarse si el volumen de importación crece.
- **Trocear la importación en varias transacciones (una por chunk)**: se descartó
  porque rompe la semántica all-or-nothing — un fallo a mitad de planilla dejaría
  un import parcial que el usuario tendría que deshacer a mano. `flush()+clear()`
  dentro de la misma transacción logra el mismo efecto sobre el persistence
  context sin tocar la atomicidad.
- **Paralelizar el procesamiento de filas**: se descartó porque el cuello no era
  CPU sino el patrón de acceso a datos; paralelizar dentro de una transacción JPA
  además no es viable (la sesión de Hibernate no es thread-safe).
- **Parser streaming (SAX) de Apache POI**: se descartó porque el parseo del
  workbook no era el cuello de botella; las planillas reales entran cómodas en
  memoria con el modelo actual.
