---
name: migracion
description: Escribe y verifica migraciones de Flyway para la base de SIGA. Detecta qué cambió en las entidades, genera el .sql con el número de versión correcto, y lo valida corriendo la suite contra un Postgres limpio. Usar cuando se toque una entidad JPA, se pida "escribir la migración", agregar/renombrar/borrar columnas o tablas, o antes de mergear un cambio de schema. Palabras clave: migracion, flyway, schema, DDL, entidad, columna, tabla, secuencia, V2, V3, ddl-auto, validate.
---

# Migraciones de Flyway en SIGA

Este repo usa Flyway desde el 05/09/2026. El contexto completo de por qué y cómo se adoptó
está en `plans/flyway-impl/README.md` — **leerlo antes de la primera migración de la
sesión**, tiene los hallazgos que no se deducen del código.

## Las dos reglas que no se rompen

**1. Una migración ya aplicada no se toca nunca más.** Flyway le guarda un checksum; editar
el archivo hace fallar el arranque en todos los ambientes donde ya corrió. Si algo salió
mal, va una migración nueva encima. **Nunca hacia atrás.**

Antes de editar cualquier `V*.sql` existente, verificar si ya se aplicó:

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
```

Se puede consultar dev en tiempo real con el MCP `postgres-readonly`. Si la versión figura
ahí, **el archivo es intocable**: proponer una migración nueva y explicar por qué.

La única excepción es una migración que falló (`success = false`): esa nunca llegó a
aplicarse bien, se corrige el `.sql` y se borra la fila fallida antes de reintentar.

**2. No se mergea un cambio de entidad sin su migración.** Los tests lo atajan, pero hay
que tenerlo presente al revisar un PR.

## Qué NO tocar

- **`V1__baseline_schema.sql`** es la foto del schema de dev del 04/09/2026, ya baselineada
  en dev y test. Congelada. No se edita ni para arreglar algo que esté mal ahí: va una
  migración nueva.
- Los perfiles `dev-local` (Flyway apagado a propósito, manda `ddl-auto: update`) y la
  config de `application.yaml`, salvo que el usuario lo pida explícitamente.

## Flujo para escribir una migración

### 1. Ver qué cambió realmente

No escribir el SQL de memoria ni deducirlo de la entidad. Sacarlo de un diff real entre el
schema que Hibernate armó en `dev-local` y el de dev:

```bash
/opt/homebrew/opt/libpq/bin/pg_dump -s --no-owner --no-privileges \
  -h localhost -p 5432 -U myuser -d mydatabase > plans/dev-local.sql

python3 plans/flyway-impl/pgdiff.py plans/post-dev.sql plans/dev-local.sql
```

Usar **siempre** los binarios de libpq 18 (`/opt/homebrew/opt/libpq/bin/`): el `pg_dump` 14
del PATH no entiende la directiva `\restrict` que emite pg_dump 18.

Si `plans/post-dev.sql` no existe o está viejo, regenerarlo:

```bash
/opt/homebrew/opt/libpq/bin/pg_dump -s --no-owner --no-privileges \
  -h 100.90.179.1 -p 5432 -U admin_root_dev -d database_dev > plans/post-dev.sql
```

`pgdiff.py` **no compara** funciones, vistas, triggers ni incrementos de secuencias. Para
esos, hacer además un diff textual normalizado de los dos dumps (ignorando comentarios,
líneas en blanco, `SET`, `\restrict` y `COMMENT ON SCHEMA`).

### 2. Elegir el número de versión

El siguiente entero libre en `src/main/resources/db/migration/`. Verificar con `ls` — nunca
asumir.

Nombre: `V<n>__<descripcion_en_snake_case>.sql`. **Dos guiones bajos** entre versión y
descripción: con uno solo Flyway ignora el archivo en silencio.

### 3. Escribir el .sql

- SQL de PostgreSQL 16 directo. Nada de abstracciones.
- Un encabezado corto que diga **por qué**, no qué (el qué se lee en el SQL).
- Idempotente cuando sea barato (`IF NOT EXISTS`, `IF EXISTS`): dev y test suelen estar
  parcialmente arreglados a mano, y una migración que ya está aplicada de hecho no debe
  fallar.
- Si la migración toca datos además de schema, decirlo en el encabezado.

### 4. Verificar

```bash
./mvnw test
```

Los tests de integración levantan un Postgres limpio, aplican **todas** las migraciones
desde la V1 y después corren `ddl-auto: validate` contra las entidades. Es la única
verificación automática que existe: si pasa, la migración está bien.

**Al leer el resultado de Maven, mirar el exit code de Maven, no el de un `tail` en un
pipe.** Redirigir a archivo y chequear `$?` directamente.

## Cosas que ya nos mordieron

- **`ddl-auto: validate` no verifica foreign keys.** Solo tablas y columnas. Una FK que
  existe en la base pero no en el mapeo (ids como `Long` suelto en vez de `@ManyToOne`) no
  la detecta nadie hasta que un INSERT la viola.
- **Un `allocationSize` que no coincide con el `INCREMENT` de la secuencia aborta el
  arranque.** Hibernate 6+ trae `SequenceMismatchStrategy = EXCEPTION`. Si una migración
  toca el incremento de una secuencia, hay que tocar la entidad en el mismo commit.
- **Las secuencias de columnas `serial`/`IDENTITY` no se pueden manipular sueltas** en
  algunos casos (ownership, por ejemplo): siguen a su tabla. Las de `@SequenceGenerator` sí
  son independientes — hoy son `evento_academico_id_evento_academico_seq`,
  `ocurrencia_id_ocurrencia_seq` y `asignacion_aula_id_asignacion_seq`.
- **Los tests ya no arrancan con la base vacía entre contextos de Spring.** Se pasó de
  `create-drop` a `validate`, así que los datos persisten dentro de una corrida. Un fixture
  nuevo que inserte un valor único hardcodeado va a chocar; uniquificarlo
  (`"Tipo Test " + System.nanoTime()`).
- **Tres diferencias cosméticas aparecen siempre** al diffear dumps, en
  `chk_solicitud_aula_tipo`, `solicitud_aula_aud_tipo_solicitud_check` y
  `solicitud_aula_item_dia_semana_check`: `ANY((ARRAY[...])::text[])` contra
  `ANY(ARRAY[(...)::text, ...])`. Es un artefacto de deparse de `pg_restore`, mismos
  valores. **Ignorarlas.**

## Límites: qué no hace este skill

- **No aplica migraciones a dev ni a test.** Eso lo hace la app al arrancar. El MCP
  `postgres-readonly` es de solo lectura y solo llega a dev.
- **No commitea, pushea ni mergea.** Devolver el comando como texto (ver `CLAUDE.md`).
- **No decide la intención de negocio.** Si el diff es ambiguo — un renombre de columna se
  ve igual que un DROP + ADD, y confundirlos pierde datos — preguntar antes de escribir.
