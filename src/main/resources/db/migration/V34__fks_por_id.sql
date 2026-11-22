-- References by id without a foreign key. Added NOT VALID so existing rows are not scanned;
-- new writes are checked. Validate later with ALTER TABLE <t> VALIDATE CONSTRAINT <name>.
-- ocurrencia.id_ocurrencia_principal stays without FK: RecurringEventReconciler deletes occurrences in bulk.
ALTER TABLE solicitud_aula_item ADD CONSTRAINT fk_solicitud_item_edificio_derivado
    FOREIGN KEY (id_edificio_derivado) REFERENCES edificio (id_edificio) NOT VALID;
ALTER TABLE solicitud_aula_item ADD CONSTRAINT fk_solicitud_item_edificio_devuelto
    FOREIGN KEY (id_edificio_devuelto) REFERENCES edificio (id_edificio) NOT VALID;
ALTER TABLE solicitud_aula_item ADD CONSTRAINT fk_solicitud_item_evento_recurrente
    FOREIGN KEY (id_evento_recurrente) REFERENCES evento_recurrente (id_evento_academico) NOT VALID;
ALTER TABLE usuario_rol ADD CONSTRAINT fk_usuario_rol_alcance_edificio
    FOREIGN KEY (id_alcance) REFERENCES edificio (id_edificio) NOT VALID;

CREATE INDEX idx_solicitud_item_edificio_derivado ON solicitud_aula_item (id_edificio_derivado) WHERE id_edificio_derivado IS NOT NULL;
CREATE INDEX idx_solicitud_item_edificio_devuelto ON solicitud_aula_item (id_edificio_devuelto) WHERE id_edificio_devuelto IS NOT NULL;
CREATE INDEX idx_solicitud_item_evento_recurrente ON solicitud_aula_item (id_evento_recurrente) WHERE id_evento_recurrente IS NOT NULL;
CREATE INDEX idx_usuario_rol_alcance ON usuario_rol (id_alcance) WHERE id_alcance IS NOT NULL;
