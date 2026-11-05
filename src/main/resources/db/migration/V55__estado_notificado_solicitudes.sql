-- Estado NOTIFIED (docente notificado, esperando su respuesta) y datos de la aceptación del pedido.
-- RESOLVED pasa a significar "aceptado": los RESOLVED existentes se tratan como aceptados en el
-- momento en que se notificaron, en la primera ronda.

ALTER TABLE solicitud_aula_item DROP CONSTRAINT chk_solicitud_item_estado;
ALTER TABLE solicitud_aula_item ADD CONSTRAINT chk_solicitud_item_estado
    CHECK (estado = ANY (ARRAY['NEW', 'DERIVED_TO_BUILDING', 'IN_EVALUATION', 'NOTIFIED', 'RESOLVED', 'CANCELLED']));

ALTER TABLE solicitud_aula_item_aud DROP CONSTRAINT solicitud_aula_item_aud_estado_check;
ALTER TABLE solicitud_aula_item_aud ADD CONSTRAINT solicitud_aula_item_aud_estado_check
    CHECK (estado = ANY (ARRAY['NEW', 'DERIVED_TO_BUILDING', 'IN_EVALUATION', 'NOTIFIED', 'RESOLVED', 'CANCELLED']));

ALTER TABLE solicitud_aula_item
    ADD COLUMN plazo_aceptacion timestamp with time zone,
    ADD COLUMN fecha_aceptacion timestamp with time zone,
    ADD COLUMN aceptado_por character varying(150),
    ADD COLUMN ronda_notificacion integer DEFAULT 0 NOT NULL;

ALTER TABLE solicitud_aula_item_aud
    ADD COLUMN plazo_aceptacion timestamp with time zone,
    ADD COLUMN fecha_aceptacion timestamp with time zone,
    ADD COLUMN aceptado_por character varying(150),
    ADD COLUMN ronda_notificacion integer;

ALTER TABLE solicitud_aula_item ADD CONSTRAINT chk_solicitud_item_ronda_notificacion
    CHECK (ronda_notificacion >= 0);

UPDATE solicitud_aula_item
SET fecha_aceptacion = fecha_notificacion,
    aceptado_por = decidido_por,
    ronda_notificacion = 1
WHERE estado = 'RESOLVED';

-- Direcciones de Reply-To del mail, separadas por coma: el envío se reintenta desde esta tabla.
ALTER TABLE notificacion ADD COLUMN responder_a character varying(1000);
