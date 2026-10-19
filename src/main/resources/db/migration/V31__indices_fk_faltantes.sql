-- Foreign keys without a covering index: each DELETE on the parent scanned the whole child table.
-- Nullable columns get a partial index since most rows are NULL.
CREATE INDEX idx_solicitud_item_asignacion_ocurrencia ON solicitud_item_asignacion (id_ocurrencia);
CREATE INDEX idx_solicitud_item_asignacion_aula ON solicitud_item_asignacion (id_aula);
CREATE INDEX idx_solicitud_item_aula_anterior ON solicitud_aula_item (id_aula_anterior) WHERE id_aula_anterior IS NOT NULL;
CREATE INDEX idx_ocurrencia_aula_anterior ON ocurrencia (id_aula_anterior) WHERE id_aula_anterior IS NOT NULL;
CREATE INDEX idx_refresh_token_reemplazado_por ON refresh_token (reemplazado_por) WHERE reemplazado_por IS NOT NULL;
