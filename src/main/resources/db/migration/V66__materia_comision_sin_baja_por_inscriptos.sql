DO $$
DECLARE
    v_rev integer;
    v_ids bigint[];
    v_id bigint;
BEGIN
    SELECT array_agg(id_materia_comision ORDER BY id_materia_comision) INTO v_ids
      FROM public.materia_comision
     WHERE motivo_baja = 'NO_ENROLLED';

    IF v_ids IS NULL THEN
        RETURN;
    END IF;

    INSERT INTO public.revinfo (fecha_revision, usuario, tipo_actor, descripcion, operacion_id, usuario_bd)
    VALUES (clock_timestamp() AT TIME ZONE 'America/Argentina/Buenos_Aires', NULL, 'HUMAN',
            'Corrección manual: Reactivación de materia-comisión dada de baja por SysAcad sin inscriptos',
            gen_random_uuid()::text, left(session_user::text, 63))
    RETURNING rev INTO v_rev;

    UPDATE public.materia_comision
       SET eliminado_en = NULL, motivo_baja = NULL
     WHERE id_materia_comision = ANY (v_ids);

    FOREACH v_id IN ARRAY v_ids LOOP
        PERFORM public.auditoria_registrar(v_rev, 'materia_comision_aud', v_id::text, 1);
    END LOOP;

    UPDATE public.revinfo
       SET tipo_actor = 'SYSTEM',
           descripcion = 'Reactivación de materia-comisión dada de baja por SysAcad sin inscriptos'
     WHERE rev = v_rev;
END
$$;

ALTER TABLE materia_comision DROP CONSTRAINT chk_materia_comision_motivo_baja;
ALTER TABLE materia_comision DROP COLUMN motivo_baja;
