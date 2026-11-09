-- Aula y origen que tenía cada ocurrencia antes del primer assign del pedido. Reabrir o cancelar
-- el pedido restaura la clase a esa aula, conservando el origen (SYSACAD, MANUAL, etc.).

ALTER TABLE solicitud_item_asignacion ADD COLUMN id_aula_original bigint;
ALTER TABLE solicitud_item_asignacion ADD COLUMN origen_original character varying(20);
-- false en las filas anteriores a V60: ahí el original sale de id_aula_anterior del ítem.
ALTER TABLE solicitud_item_asignacion ADD COLUMN original_capturado boolean NOT NULL DEFAULT false;
ALTER TABLE solicitud_item_asignacion ADD CONSTRAINT fk_solicitud_item_asignacion_aula_original
    FOREIGN KEY (id_aula_original) REFERENCES aula(id_aula);
CREATE INDEX idx_solicitud_item_asignacion_aula_original
    ON solicitud_item_asignacion (id_aula_original) WHERE id_aula_original IS NOT NULL;

ALTER TABLE solicitud_item_asignacion_aud ADD COLUMN id_aula_original bigint;
ALTER TABLE solicitud_item_asignacion_aud ADD COLUMN origen_original character varying(20);
ALTER TABLE solicitud_item_asignacion_aud ADD COLUMN original_capturado boolean;

-- Día en que se reabrió desde NOTIFIED; el assign lo limpia. Lo usa el vencimiento de un reabierto.
ALTER TABLE solicitud_aula_item ADD COLUMN reabierto_desde date;
ALTER TABLE solicitud_aula_item_aud ADD COLUMN reabierto_desde date;
