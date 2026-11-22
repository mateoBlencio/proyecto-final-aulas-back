-- CHECK constraints missing on enum-like columns and simple ranges. Added NOT VALID so existing rows
-- are not scanned (a bad legacy row must not block the deploy); new writes are checked right away.
-- Once the existing data is confirmed clean: ALTER TABLE <t> VALIDATE CONSTRAINT <name>.
ALTER TABLE aula ADD CONSTRAINT chk_aula_modo_permiso
    CHECK (modo_permiso IN ('ALL', 'NONE', 'SUBSET')) NOT VALID;
ALTER TABLE aula_permiso ADD CONSTRAINT chk_aula_permiso_tipo_objetivo
    CHECK (tipo_objetivo IN ('SUBJECT')) NOT VALID;
ALTER TABLE tipo_recurso ADD CONSTRAINT chk_tipo_recurso_tipo_valor
    CHECK (tipo_valor IN ('COUNT', 'BOOLEAN')) NOT VALID;
ALTER TABLE aula_recurso ADD CONSTRAINT chk_aula_recurso_cantidad
    CHECK (cantidad >= 0) NOT VALID;
ALTER TABLE notificacion ADD CONSTRAINT chk_notificacion_estado
    CHECK (estado IN ('PENDING', 'SENT', 'FAILED', 'DISCARDED')) NOT VALID;
ALTER TABLE notificacion ADD CONSTRAINT chk_notificacion_canal
    CHECK (canal IN ('EMAIL')) NOT VALID;
ALTER TABLE archivado_auditoria ADD CONSTRAINT chk_archivado_auditoria_estado
    CHECK (estado IN ('IN_PROGRESS', 'COMPLETED', 'STOPPED_BY_CONCURRENT_RUN', 'FAILED')) NOT VALID;
ALTER TABLE regeneracion_tablero ADD CONSTRAINT chk_regeneracion_tablero_mes
    CHECK (mes BETWEEN 1 AND 12) NOT VALID;
ALTER TABLE periodo_academico ADD CONSTRAINT chk_periodo_receso
    CHECK (fecha_fin_receso IS NULL OR fecha_inicio_receso IS NULL OR fecha_fin_receso >= fecha_inicio_receso) NOT VALID;
ALTER TABLE ocurrencia ADD CONSTRAINT chk_ocurrencia_orden_aula
    CHECK (orden_aula >= 1) NOT VALID;
-- GLOBAL roles carry no building; BUILDING roles must carry one.
ALTER TABLE usuario_rol ADD CONSTRAINT chk_usuario_rol_alcance
    CHECK ((tipo_alcance = 'GLOBAL' AND id_alcance IS NULL) OR (tipo_alcance = 'BUILDING' AND id_alcance IS NOT NULL)) NOT VALID;
-- A request item is either a single date or a weekly class day, never both.
ALTER TABLE solicitud_aula_item ADD CONSTRAINT chk_solicitud_item_momento
    CHECK (fecha IS NULL OR dia_semana IS NULL) NOT VALID;
