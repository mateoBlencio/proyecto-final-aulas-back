DO $$
DECLARE
    v_rev integer;
    v_ids bigint[];
    v_id bigint;
BEGIN
    SELECT array_agg(e.id_especialidad ORDER BY e.id_especialidad) INTO v_ids
      FROM public.especialidad e
     WHERE e.eliminado_en IS NOT NULL
       AND EXISTS (SELECT 1
                     FROM public.especialidad_aud a
                     JOIN public.revinfo r ON r.rev = a.rev
                    WHERE a.id_especialidad = e.id_especialidad
                      AND a.revtype = 0
                      AND a.eliminado_en = e.eliminado_en
                      AND r.descripcion <> 'Estado inicial de catálogos');

    IF v_ids IS NULL THEN
        RETURN;
    END IF;

    INSERT INTO public.revinfo (fecha_revision, usuario, tipo_actor, descripcion, operacion_id, usuario_bd)
    VALUES (clock_timestamp() AT TIME ZONE 'America/Argentina/Buenos_Aires', NULL, 'HUMAN',
            'Corrección manual: Reactivación de especialidades creadas dadas de baja por el sync de SysAcad',
            gen_random_uuid()::text, left(session_user::text, 63))
    RETURNING rev INTO v_rev;

    UPDATE public.especialidad SET eliminado_en = NULL WHERE id_especialidad = ANY (v_ids);

    FOREACH v_id IN ARRAY v_ids LOOP
        PERFORM public.auditoria_registrar(v_rev, 'especialidad_aud', v_id::text, 1);
    END LOOP;

    UPDATE public.revinfo
       SET tipo_actor = 'SYSTEM',
           descripcion = 'Reactivación de especialidades creadas dadas de baja por el sync de SysAcad'
     WHERE rev = v_rev;
END
$$;
