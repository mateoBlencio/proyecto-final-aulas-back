-- Copies the current rows of the catalogs audited since V49 as an ADD in their _aud tables, so the first
-- later change of a record shows its real "before" state instead of nulls. One revision for all of them,
-- opened without usuario, filled with auditoria_registrar of V18 and then marked SYSTEM. A database without
-- catalog rows is left untouched.
DO $$
DECLARE
    v_tables CONSTANT text[][] := ARRAY[
        ARRAY['edificio', 'id_edificio'],
        ARRAY['aula', 'id_aula'],
        ARRAY['tipo_aula', 'id_tipo_aula'],
        ARRAY['aula_recurso', 'id_aula_recurso'],
        ARRAY['aula_permiso', 'id_aula_permiso'],
        ARRAY['tipo_recurso', 'id_tipo_recurso'],
        ARRAY['feriado', 'fecha'],
        ARRAY['comision', 'id_comision'],
        ARRAY['materia', 'id_materia'],
        ARRAY['especialidad', 'id_especialidad'],
        ARRAY['plan_estudio', 'id_plan'],
        ARRAY['periodo_academico', 'id_periodo']
    ];
    v_rev integer;
    v_total bigint := 0;
    v_count bigint;
    v_table text;
    v_pk text;
    v_id text;
    i integer;
BEGIN
    FOR i IN 1 .. array_length(v_tables, 1) LOOP
        EXECUTE format('SELECT count(*) FROM public.%I', v_tables[i][1]) INTO v_count;
        v_total := v_total + v_count;
    END LOOP;
    IF v_total = 0 THEN
        RETURN;
    END IF;

    INSERT INTO public.revinfo (fecha_revision, usuario, tipo_actor, descripcion, operacion_id, usuario_bd)
    VALUES (clock_timestamp() AT TIME ZONE 'America/Argentina/Buenos_Aires', NULL, 'HUMAN',
            'Corrección manual: Estado inicial de catálogos', gen_random_uuid()::text, left(session_user::text, 63))
    RETURNING rev INTO v_rev;

    FOR i IN 1 .. array_length(v_tables, 1) LOOP
        v_table := v_tables[i][1];
        v_pk := v_tables[i][2];
        FOR v_id IN EXECUTE format('SELECT %I::text FROM public.%I ORDER BY %I', v_pk, v_table, v_pk) LOOP
            PERFORM public.auditoria_registrar(v_rev, v_table || '_aud', v_id, 0);
        END LOOP;
    END LOOP;

    -- auditoria_registrar only accepts a HUMAN "Corrección manual:" revision; once filled it becomes a system one.
    UPDATE public.revinfo SET tipo_actor = 'SYSTEM', descripcion = 'Estado inicial de catálogos' WHERE rev = v_rev;
END
$$;
