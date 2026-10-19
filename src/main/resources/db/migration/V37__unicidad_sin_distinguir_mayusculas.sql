-- Java already rejects "Proyector" when "proyector" exists, but two concurrent inserts could both pass.
-- Fails if the table already holds names that differ only by case.
ALTER TABLE tipo_recurso DROP CONSTRAINT uq_tipo_recurso_nombre;
CREATE UNIQUE INDEX uq_tipo_recurso_nombre_lower ON tipo_recurso (lower(nombre));

ALTER TABLE tipo_aula DROP CONSTRAINT tipo_aula_descripcion_key;
CREATE UNIQUE INDEX uq_tipo_aula_descripcion_lower ON tipo_aula (lower(descripcion));
