# Gestión del esquema de base de datos

SIGA usa una base de datos compartida y **no cuenta con una herramienta de
migraciones** (por ejemplo Flyway o Liquibase). El esquema se actualiza a
mano sobre esa base compartida.

## Flujo de trabajo

1. Cuando un cambio en el código requiere una modificación del esquema
   (nueva tabla, columna, índice, etc.), se genera un script SQL numerado
   en esta carpeta, con el nombre `NNN_descripcion.sql` (por ejemplo
   `001_envers_tablas_auditoria.sql`), donde `NNN` es correlativo respecto
   de los scripts ya existentes.
2. El script se agrega a la tabla de estado de este README, con estado
   `pendiente`.
3. Alguien con acceso a la base de datos compartida aplica el script a
   mano y actualiza su estado a `aplicado`.
4. Los scripts ya aplicados **no se modifican**: si el esquema necesita un
   cambio adicional, se agrega un nuevo script numerado.

## Elementos obsoletos

Los elementos del esquema que quedan obsoletos (columnas, tablas) **no se
eliminan** de la base de datos compartida. Se documentan en
`docs/ddl/obsoletos.md` (a crear cuando exista el primer caso), indicando
qué elemento quedó obsoleto, por qué y desde cuándo, para poder
eliminarlo más adelante cuando la base de datos compartida lo permita.

## Estado de los scripts

| Script | Descripción | Estado |
|--------|-------------|--------|
| `001_envers_tablas_auditoria.sql` | Tablas de auditoría de Hibernate Envers (`revinfo` + `*_aud`) para `allocation` (ver ADR-007) | pendiente |
