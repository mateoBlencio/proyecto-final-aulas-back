-- Tables and columns that no entity or query uses.
DROP TABLE IF EXISTS notificacion_saliente;

ALTER TABLE solicitud_aula_item DROP COLUMN IF EXISTS id_aula_actual;
ALTER TABLE solicitud_aula_item DROP COLUMN IF EXISTS cantidad_inscriptos;
ALTER TABLE evento_academico DROP COLUMN IF EXISTS eliminado;
ALTER TABLE ocurrencia DROP COLUMN IF EXISTS eliminado;

-- fecha_creacion duplicates creado_en. The audit archive copies rows with SELECT *, so the
-- archivo copy must lose the column too to keep both layouts identical.
DROP INDEX IF EXISTS idx_solicitud_aula_fecha_creacion;
ALTER TABLE solicitud_aula DROP COLUMN IF EXISTS fecha_creacion;
ALTER TABLE asignacion_aula DROP COLUMN IF EXISTS fecha_creacion;
ALTER TABLE asignacion_aula_aud DROP COLUMN IF EXISTS fecha_creacion;
ALTER TABLE archivo.asignacion_aula_aud DROP COLUMN IF EXISTS fecha_creacion;
