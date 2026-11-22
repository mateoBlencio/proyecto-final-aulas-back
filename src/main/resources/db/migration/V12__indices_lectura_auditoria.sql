-- Índices para la lectura agregada del registro de auditoría (AuditLogQueryRepository).
-- Las tablas _aud tienen PK (id, rev) y ningún índice que empiece por rev: el listado hace
-- JOIN/EXISTS por x.rev = r.rev contra revinfo, y sin estos índices recorre la tabla entera.
-- idx_revinfo_fecha_revision acelera el filtro por rango de fechas sobre revinfo.
CREATE INDEX IF NOT EXISTS idx_asignacion_aula_aud_rev ON asignacion_aula_aud (rev);
CREATE INDEX IF NOT EXISTS idx_usuario_aud_rev ON usuario_aud (rev);
CREATE INDEX IF NOT EXISTS idx_usuario_rol_aud_rev ON usuario_rol_aud (rev);
CREATE INDEX IF NOT EXISTS idx_evento_academico_aud_rev ON evento_academico_aud (rev);
CREATE INDEX IF NOT EXISTS idx_ocurrencia_aud_rev ON ocurrencia_aud (rev);
CREATE INDEX IF NOT EXISTS idx_solicitud_aula_aud_rev ON solicitud_aula_aud (rev);
CREATE INDEX IF NOT EXISTS idx_solicitud_aula_item_aud_rev ON solicitud_aula_item_aud (rev);
CREATE INDEX IF NOT EXISTS idx_solicitud_aula_preferencia_aud_rev ON solicitud_aula_preferencia_aud (rev);
CREATE INDEX IF NOT EXISTS idx_solicitud_item_asignacion_aud_rev ON solicitud_item_asignacion_aud (rev);
CREATE INDEX IF NOT EXISTS idx_configuracion_aud_rev ON configuracion_aud (rev);
CREATE INDEX IF NOT EXISTS idx_revinfo_fecha_revision ON revinfo (fecha_revision);
