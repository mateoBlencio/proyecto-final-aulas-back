-- Indexes for the aggregate read of the audit log (AuditLogQueryRepository).
-- The _aud tables have PK (id, rev) and no index starting with rev: the listing does
-- JOIN/EXISTS on x.rev = r.rev against revinfo, and without these indexes it scans the whole table.
-- idx_revinfo_fecha_revision speeds up the date-range filter on revinfo.
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
