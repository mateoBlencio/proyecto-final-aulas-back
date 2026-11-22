-- Indexes on enum columns with a handful of values; dev stats showed 0 or 1 scans.
DROP INDEX IF EXISTS idx_asignacion_source;
DROP INDEX IF EXISTS idx_ocurrencia_status;
DROP INDEX IF EXISTS idx_evento_academico_tipo;
DROP INDEX IF EXISTS idx_evento_recurrente_day;
DROP INDEX IF EXISTS idx_evento_unico_tipo_actividad;
