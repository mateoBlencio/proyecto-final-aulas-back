-- Java already rejects "Proyector" when "proyector" exists, but two concurrent inserts could both pass.
-- Fails if the table already holds names that differ only by case. Check first on dev/prod (both must return 0 rows):
--   SELECT lower(nombre), count(*) FROM tipo_recurso GROUP BY 1 HAVING count(*) > 1;
--   SELECT lower(descripcion), count(*) FROM tipo_aula GROUP BY 1 HAVING count(*) > 1;
ALTER TABLE tipo_recurso DROP CONSTRAINT uq_tipo_recurso_nombre;
CREATE UNIQUE INDEX uq_tipo_recurso_nombre_lower ON tipo_recurso (lower(nombre));

ALTER TABLE tipo_aula DROP CONSTRAINT tipo_aula_descripcion_key;
CREATE UNIQUE INDEX uq_tipo_aula_descripcion_lower ON tipo_aula (lower(descripcion));
