-- La clave natural de la comisión es (codigo_curso, id_periodo_academico): el sync de Comisiones y el
-- resolver de EVENTOS/ASIGNACIONES la usan como única. Si ya hay duplicados la migración se detiene con un
-- mensaje claro en vez de fusionar o borrar filas: hay que resolverlos a mano (las comisiones tienen
-- materias, eventos y solicitudes colgando).
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
