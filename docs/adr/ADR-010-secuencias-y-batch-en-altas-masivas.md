# ADR-010: PKs por `SEQUENCE` y escritura batcheada de una sola pasada en altas masivas

## Estado

Aceptado — 2026-07-20

## Contexto

ADR-009 acotó el costo de la importación Excel (prefetch en batch, contexto
acotado, `hibernate.jdbc.batch_size`) pero dejó explícitamente sin resolver el
INSERT batcheado, porque las tres entidades del dominio de asignación
(`AcademicEvent`, `Occurrence`, `Allocation`) usaban `GenerationType.IDENTITY`:
Hibernate desactiva el batching de INSERT con `IDENTITY` (necesita la PK
generada por la BD antes de poder encolar la fila siguiente), así que
`batch_size` solo aplicaba a UPDATE. Cada INSERT viajaba como round-trip
individual, multiplicado por N filas de Excel o N occurrences de un preview de
auto-asignación confirmado.

Además, con el prefetch de ADR-009 ya en batch por evento, seguían quedando
dos puntos con **una query por evento** en vez de una sola para todo el lote:

1. `ExcelImportServiceImpl` llamaba `AllocationService
   .importAllocationsFromDate(...)` fila por fila; cada llamada repetía sus 4
   queries propias (evento, aula, occurrences, asignaciones existentes) — con
   ~1300 filas, ~5200 round-trips solo para la parte de asignación.
2. `AutoAllocationServiceImpl.confirm` agrupaba las occurrences del preview por
   evento (aula distinta por evento) y llamaba `AllocationWriter.apply` **por
   grupo**, repitiendo la query de asignaciones existentes por cada evento del
   preview en vez de una sola para todo el conjunto.

## Decisión

- **PKs `IDENTITY` → `SEQUENCE`** en `AcademicEvent`, `Occurrence` y
  `Allocation`, con `@SequenceGenerator(allocationSize = 50)` apuntando a las
  secuencias `BIGSERIAL` ya existentes (creadas por el DBA), alteradas a
  `INCREMENT BY 50` para que coincidan con el `allocationSize` de Hibernate.
  DDL en `.claude/sql/perf-batch-inserts.sql`, aplicado a la BD docker local;
  pendiente entregar a la DBA para dev/test/prod (esquema externo, sin
  Flyway/Liquibase — ver reglas de BD del proyecto). Esto revierte la
  alternativa descartada en ADR-009 ("cambiar PKs IDENTITY → SEQUENCE"): el
  volumen de importación creció lo suficiente para justificar el ciclo DDL.
- **`reWriteBatchedInserts: true`** en `spring.datasource.hikari
  .data-source-properties` (`application.yaml`, perfil base). Es propiedad de
  Hikari y no de la URL JDBC para que sobreviva el override completo de
  `datasource.url` en `application-dev-local.yaml`. Con esto pgjdbc reescribe
  los INSERT batcheados en un solo `INSERT ... VALUES (...),(...),(...)` en
  vez de N round-trips individuales, incluso ya con `SEQUENCE` + batch_size.
- **`AllocationWriter.apply`** gana un overload con
  `Function<Occurrence, Integer> classroomIdResolver` en vez de un
  `Integer classroomId` fijo, para poder escribir en una sola pasada
  occurrences de **varios eventos con aula distinta cada uno** (una única
  query `findByOccurrence_IdIn` de asignaciones existentes para todo el lote).
  El overload de aula única se conserva y delega al nuevo (`o -> classroomId`).
- **`AutoAllocationServiceImpl.confirm`** elimina el loop
  `groupingBy(evento) + writer.apply` por grupo; usa el nuevo overload para
  todo el preview en una sola llamada.
- **`AllocationService.importAllocationsBatch`** (nuevo) — batch de
  `importAllocationsFromDate` para una importación completa: una sola
  `validateClassroomsAvailable` y un solo `AllocationWriter.apply` para todos
  los eventos del archivo. Asume que cada `recurringEventId` ya viene de
  `findOrCreateRecurringEvent`, por lo que no repite esa validación.
  `OccurrenceRepository.findByEvent_IdInAndDateGreaterThanEqual` (nuevo) trae
  las occurrences de todos los eventos del lote en una query, usando la fecha
  más antigua del batch (sobre-trae); el caller filtra en memoria por el
  `fromDate` propio de cada evento.
- **`ExcelImportServiceImpl`** ya no llama a la fachada de asignación dentro
  del loop de filas; acumula los DTOs en una lista y hace un único
  `importAllocationsBatch(...)` al final. Se agrega instrumentación con
  `System.nanoTime()` por fase (datos de referencia, creación de evento,
  asignación), logueada al completar el import.
- `application-dev-local.yaml`: `show-sql: false` / `org.hibernate.SQL: WARN`
  (estaban en `DEBUG`, generan ruido y overhead con miles de INSERT logueados
  fila por fila).

## Consecuencias

- Las tres entidades ya no dependen de la PK generada por la BD para
  encadenar el siguiente INSERT: Hibernate puede agruparlos, y pgjdbc los
  reescribe en un solo `VALUES` multi-fila.
- El import de Excel pasa de ~5200 round-trips de asignación (uno por fila) a
  1 query de validación + 1 de occurrences + N INSERT/UPDATE batcheados. El
  confirm de auto-asignación pasa de 1 query de asignaciones existentes por
  evento del preview a 1 sola para todo el preview.
- Deuda de esquema: `allocationSize = 50` en las entidades debe mantenerse en
  sync con `INCREMENT BY 50` de las secuencias en BD. Si alguien cambia uno
  sin el otro, se generan huecos o colisiones de PK. Vive fuera del control de
  la app (esquema externo) — documentado en el DDL pendiente para la DBA.
- Los `id` generados ya no son estrictamente consecutivos fila a fila (se
  reservan de a 50 por instancia de la app); no hay código que asuma
  consecutividad, así que no es un cambio de comportamiento observable.
- `importAllocationsFromDate` (el método fila-a-fila) se conserva porque tiene
  otro caller productivo (asignación manual desde fecha); `importAllocationsBatch`
  es exclusivo del import Excel, mismo patrón de "intent method acotado a su
  único caller" que estableció ADR-009.
- Pendiente: medir con datos reales (668 eventos / 42583 occurrences, seed en
  `.claude/sql/seed-eventos-recurrentes.sql`) el tiempo total de import y de
  confirm con estos cambios aplicados, y comparar contra el baseline previo.
- Sin relación con este ADR: persiste un problema de encoding (tildes llegan
  como `??` al dar de alta asignaciones) — servidor/cliente Postgres en UTF8,
  no es la causa; falta revisar `file.encoding` de la JVM o el encoding del
  request HTTP / lectura de Excel.

## Alternativas consideradas

- **Bulk insert crudo por JDBC (bypass Hibernate)**: descartado porque
  rompería el audit trail de Envers — las tablas `_aud` se llenan vía
  listeners de ciclo de vida de entidad, que un INSERT directo por JDBC no
  dispara.
- **UUID como PK en vez de `SEQUENCE`**: descartado. Peor fit para este caso:
  bloat de índice por inserción no secuencial, y migración mucho más grande
  (FKs + tablas `_aud` de Envers) para un problema que `SEQUENCE` con
  `allocationSize` ya resuelve.
- **Mantener `importAllocationsFromDate` fila por fila y solo agregar
  `reWriteBatchedInserts`**: insuficiente por sí solo — el cuello principal
  del import no era el formato del INSERT sino repetir 4 queries de lectura
  por fila; había que resolver el N+1 de lectura además del batching de
  escritura.