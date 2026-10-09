-- Indices (id, rev) para la busqueda de la fila previa del diff por campo (AuditRecordStateRepository).
-- Las _aud con PK (rev, id) no sirven para "WHERE id = ? AND rev < ? ORDER BY rev DESC LIMIT 1".
-- Excluidas por tener ya la PK (id, rev): configuracion_aud, usuario_aud, usuario_rol_aud,
-- solicitud_item_asignacion_aud.
CREATE INDEX IF NOT EXISTS idx_asignacion_aula_aud_id_rev ON asignacion_aula_aud (id_asignacion, rev);
CREATE INDEX IF NOT EXISTS idx_evento_academico_aud_id_rev ON evento_academico_aud (id_evento_academico, rev);
CREATE INDEX IF NOT EXISTS idx_evento_recurrente_aud_id_rev ON evento_recurrente_aud (id_evento_academico, rev);
CREATE INDEX IF NOT EXISTS idx_evento_unico_aud_id_rev ON evento_unico_aud (id_evento_academico, rev);
CREATE INDEX IF NOT EXISTS idx_ocurrencia_aud_id_rev ON ocurrencia_aud (id_ocurrencia, rev);
CREATE INDEX IF NOT EXISTS idx_solicitud_aula_aud_id_rev ON solicitud_aula_aud (id_solicitud, rev);
CREATE INDEX IF NOT EXISTS idx_solicitud_aula_item_aud_id_rev ON solicitud_aula_item_aud (id_item, rev);
CREATE INDEX IF NOT EXISTS idx_solicitud_aula_preferencia_aud_id_rev ON solicitud_aula_preferencia_aud (id_preferencia, rev);
