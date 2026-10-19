-- Indexes whose columns are a leading prefix of another index or key on the same table.
-- Covered by uq_ocurrencia_evento_fecha_slot (id_evento_academico, fecha, orden_aula).
DROP INDEX IF EXISTS idx_ocurrencia_evento;
DROP INDEX IF EXISTS idx_ocurrencia_evento_fecha;
-- Covered by uq_materia_plan_codigo (id_plan, codigo_materia).
DROP INDEX IF EXISTS idx_materia_plan;
-- Covered by the primary key (id_materia, id_comision).
DROP INDEX IF EXISTS idx_materia_comision_materia;
-- Covered by uq_solicitud_item_orden (id_solicitud, orden).
DROP INDEX IF EXISTS idx_solicitud_item_solicitud;
-- Covered by uq_solicitud_preferencia_item_aula (id_item, id_aula).
DROP INDEX IF EXISTS idx_solicitud_preferencia_item;
-- Covered by uq_solicitud_item_asignacion_item_ocurrencia (id_item, id_ocurrencia).
DROP INDEX IF EXISTS idx_solicitud_item_asignacion_item;
-- Covered by uq_usuario_rol (id_usuario, rol, tipo_alcance, id_alcance).
DROP INDEX IF EXISTS idx_usuario_rol_usuario;
-- Audit tables whose primary key already starts with rev.
DROP INDEX IF EXISTS idx_asignacion_aula_aud_rev;
DROP INDEX IF EXISTS idx_evento_academico_aud_rev;
DROP INDEX IF EXISTS idx_ocurrencia_aud_rev;
DROP INDEX IF EXISTS idx_solicitud_aula_aud_rev;
DROP INDEX IF EXISTS idx_solicitud_aula_item_aud_rev;
DROP INDEX IF EXISTS idx_solicitud_aula_preferencia_aud_rev;
