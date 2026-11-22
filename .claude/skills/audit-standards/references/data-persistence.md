# Datos y rendimiento: PostgreSQL, Flyway, Hibernate

Vigencia: verificado el 2026-10-13.

## PostgreSQL

Fuente: https://www.postgresql.org/docs/current/ddl-constraints.html (sección Foreign Keys).
Postgres crea índice solo para PK y UNIQUE. Una FK no se indexa sola: un `DELETE` o `UPDATE`
del lado referenciado escanea la tabla que referencia, y los joins por esa columna también
sufren. La documentación recomienda indexar las columnas que referencian.

Chequeos de esquema (sobre `src/main/resources/db/migration/V*.sql`, fuente de verdad):
- FK sin índice que la cubra como prefijo.
- Índices redundantes (prefijo de otro índice o de la PK), en especial en tablas `*_aud`
  cuya PK ya empieza por `rev`.
- Unicidades de negocio que solo valida Java: preferir índice único parcial,
  por ejemplo `UNIQUE (id_edificio, numero) WHERE eliminado_en IS NULL`.
- `CHECK` en columnas que guardan enums y en rangos de fechas y horas.
- `timestamp` sin zona donde el dato es un instante.
- Funciones `SECURITY DEFINER` (EXECUTE se otorga a PUBLIC por defecto).
- Secuencias: que el `DEFAULT` de la columna y el `@SequenceGenerator` usen la misma.

Consultas sobre una base real (dev-snapshot, solo `SELECT`):

```sql
-- tablas con muchos seq scans
SELECT relname, seq_scan, idx_scan, n_live_tup FROM pg_stat_user_tables ORDER BY seq_scan DESC LIMIT 20;
-- índices sin uso desde el último reset de estadísticas
SELECT relname, indexrelname, idx_scan FROM pg_stat_user_indexes WHERE idx_scan = 0;
-- tamaño
SELECT relname, pg_size_pretty(pg_total_relation_size(relid)) FROM pg_statio_user_tables ORDER BY pg_total_relation_size(relid) DESC LIMIT 20;
```

Las estadísticas cubren solo el uso desde que se importó el dump: un `idx_scan = 0` en
dev-snapshot no prueba que el índice sobre en producción.

## Flyway

- Una migración aplicada no se edita; se corrige con una nueva (regla del repo).
- `ignore-migration-patterns: "*:missing,*:future"` oculta divergencias entre el repo y la base.
  Si una migración se revirtió en el repo pero ya corrió en una base, hacer una migración nueva
  que deshaga el esquema, `flyway repair` y quitar el patrón.
- Huecos de numeración: buscar en `git log --all` y en el reflog antes de suponer.

## Hibernate y JPA

Fuentes: https://vladmihalcea.com/n-plus-1-query-problem/ y
https://www.baeldung.com/spring-open-session-in-view

- `spring.jpa.open-in-view: false` (ya está). Con OSIV encendido, el lazy loading en la vista
  oculta N+1.
- N+1: colecciones o relaciones lazy recorridas en loops o en mappers de MapStruct. El slow
  query log no lo ve porque cada consulta es rápida. Arreglos baratos: `JOIN FETCH`,
  `@EntityGraph`, `hibernate.default_batch_fetch_size` (una línea, cubre los listados paginados).
- `FetchType.EAGER` en asociaciones a colecciones.
- `allocationSize=1` en secuencias de tablas con mucho insert.
- Listas sin paginar sobre tablas que crecen; caches sin `maximumSize`.
- Consultas dentro de loops y `findAll` filtrado en Java.

## DER

Generarlo en Mermaid `erDiagram` desde las migraciones, no desde las entidades. Agrupar las
tablas de Envers (`*_aud`, `revinfo`) y el esquema `archivo` en una nota para que el diagrama
siga siendo legible. Contrastar después con las entidades JPA (nullable, length, columnas sin
mapear).
