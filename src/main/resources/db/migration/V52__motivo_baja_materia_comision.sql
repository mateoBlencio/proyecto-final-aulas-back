ALTER TABLE materia_comision ADD COLUMN motivo_baja varchar(40);
ALTER TABLE materia_comision ADD CONSTRAINT chk_materia_comision_motivo_baja
    CHECK (motivo_baja IS NULL OR (motivo_baja = 'NO_ENROLLED' AND eliminado_en IS NOT NULL));
