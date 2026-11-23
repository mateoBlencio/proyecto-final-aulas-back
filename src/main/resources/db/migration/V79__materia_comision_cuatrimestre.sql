ALTER TABLE materia_comision ADD COLUMN cuatrimestre integer;
ALTER TABLE materia_comision ADD CONSTRAINT chk_materia_comision_cuatrimestre
    CHECK (cuatrimestre IN (0, 1, 2));

ALTER TABLE materia_comision_aud ADD COLUMN cuatrimestre integer;
