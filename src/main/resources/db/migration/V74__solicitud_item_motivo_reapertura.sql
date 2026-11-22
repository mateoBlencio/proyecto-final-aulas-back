-- Motivo de reapertura aparte de motivo_decision: reabrir ya no pisa el motivo de resolución parcial del assign.
ALTER TABLE solicitud_aula_item ADD COLUMN motivo_reapertura character varying(255);
ALTER TABLE solicitud_aula_item_aud ADD COLUMN motivo_reapertura character varying(255);
