-- The natural key of a commission is (codigo_curso, id_periodo_academico): the Comisiones sync and the
-- EVENTOS/ASIGNACIONES resolver treat it as unique. If duplicates already exist the migration stops with a
-- clear message instead of merging or deleting rows: they must be resolved by hand (commissions have
-- subjects, events and requests hanging from them).
DO $$
DECLARE
    duplicates integer;
BEGIN
    SELECT count(*) INTO duplicates FROM (
        SELECT 1 FROM comision GROUP BY codigo_curso, id_periodo_academico HAVING count(*) > 1
    ) d;
    IF duplicates > 0 THEN
        RAISE EXCEPTION 'V40: hay % pares (codigo_curso, id_periodo_academico) duplicados en comision; resolverlos antes de migrar', duplicates;
    END IF;
END $$;

ALTER TABLE comision ADD CONSTRAINT uq_comision_curso_periodo UNIQUE (codigo_curso, id_periodo_academico);
