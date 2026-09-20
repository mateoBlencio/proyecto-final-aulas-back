-- Renombra los estados de solicitud de aula: PENDING -> NEW, PRE_APPROVED -> IN_EVALUATION.
-- Toca datos existentes además de constraints.

UPDATE solicitud_aula_item SET estado = 'NEW' WHERE estado = 'PENDING';
UPDATE solicitud_aula_item SET estado = 'IN_EVALUATION' WHERE estado = 'PRE_APPROVED';
UPDATE solicitud_aula_item_aud SET estado = 'NEW' WHERE estado = 'PENDING';
UPDATE solicitud_aula_item_aud SET estado = 'IN_EVALUATION' WHERE estado = 'PRE_APPROVED';

ALTER TABLE solicitud_aula_item ALTER COLUMN estado SET DEFAULT 'NEW';

ALTER TABLE solicitud_aula_item DROP CONSTRAINT IF EXISTS chk_solicitud_item_estado;
ALTER TABLE solicitud_aula_item ADD CONSTRAINT chk_solicitud_item_estado
    CHECK (estado = ANY (ARRAY['NEW', 'DERIVED_TO_BUILDING', 'IN_EVALUATION', 'RESOLVED', 'CANCELLED']));

ALTER TABLE solicitud_aula_item DROP CONSTRAINT IF EXISTS chk_solicitud_item_decision;
ALTER TABLE solicitud_aula_item ADD CONSTRAINT chk_solicitud_item_decision
    CHECK ((estado = 'NEW') OR (decidido_por IS NOT NULL AND fecha_decision IS NOT NULL));

ALTER TABLE solicitud_aula_item_aud DROP CONSTRAINT IF EXISTS solicitud_aula_item_aud_estado_check;
ALTER TABLE solicitud_aula_item_aud ADD CONSTRAINT solicitud_aula_item_aud_estado_check
    CHECK (estado = ANY (ARRAY['NEW', 'DERIVED_TO_BUILDING', 'IN_EVALUATION', 'RESOLVED', 'CANCELLED']));
