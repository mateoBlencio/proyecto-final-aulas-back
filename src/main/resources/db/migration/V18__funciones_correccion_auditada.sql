-- Functions so a data fix made by SQL on an audited table leaves a trace in the audit log.
--
--   BEGIN;
--   SELECT auditoria_abrir_revision('merge comision 5D1. en 5D1', 'admin@frc.utn.edu.ar') AS rev \gset
--   CREATE TEMP TABLE afectados ON COMMIT DROP AS
--     WITH u AS (UPDATE evento_academico SET id_comision = 10 WHERE id_comision = 11
--                RETURNING id_evento_academico)
--     SELECT * FROM u;                                                       -- the fix, atomic
--   SELECT auditoria_registrar(:rev, 'evento_academico_aud', id_evento_academico, 1)
--     FROM afectados;                                                        -- one call per touched row
--   COMMIT;
--
-- Call auditoria_registrar AFTER the UPDATE/INSERT (it copies the row's current state into the _aud
-- table) and BEFORE the DELETE (it copies the last state, like Envers does on delete).
-- auditoria_registrar only accepts a revision opened by auditoria_abrir_revision (description prefix
-- 'Corrección manual:' and tipo_actor HUMAN), so a typo in :rev cannot rewrite an application revision.
-- Limits: the caller must register every row it touched (the log cannot detect a fix made without
-- these functions); the base table needs a single-column primary key; the call fails if the row is
-- already registered for that revision or does not exist.
-- auditoria_abrir_revision requires p_usuario to be an existing usuario.correo (case-insensitive,
-- disabled users accepted) and stores the correo as written in that table. It also stores the database
-- role that ran it in revinfo.usuario_bd (session_user, or 'session_user as current_user' when they
-- differ), so the declared user can be checked against the real one. Revisions created by the app
-- leave usuario_bd null. The column is not mapped in SigaRevision nor exposed by the API.
-- Full guide (Spanish): .claude/docs/audit/correcciones-sql.md

ALTER TABLE revinfo ADD COLUMN usuario_bd varchar(63);

-- Opens a revision with the same shape as the one Envers writes. Returns rev.
-- fecha_revision is local Buenos Aires time because the JVM runs in that zone (SigaApplication) and
-- the column is timestamp without time zone; it does not depend on the session TimeZone.
-- Limits: description 236 chars (revinfo.descripcion is varchar(255) minus the 19-char prefix), user 255.
-- The actor is always HUMAN: a person runs this by hand.
CREATE FUNCTION auditoria_abrir_revision(p_descripcion text, p_usuario text) RETURNS integer
LANGUAGE plpgsql SET search_path = public, pg_temp AS $$
DECLARE
    v_rev integer;
    v_correo text;
    v_usuario_bd text;
BEGIN
    IF p_descripcion IS NULL OR btrim(p_descripcion) = '' THEN
        RAISE EXCEPTION 'auditoria_abrir_revision: p_descripcion is required';
    END IF;
    IF p_usuario IS NULL OR btrim(p_usuario) = '' THEN
        RAISE EXCEPTION 'auditoria_abrir_revision: p_usuario is required';
    END IF;

    IF length(p_descripcion) > 236 THEN
        RAISE EXCEPTION 'auditoria_abrir_revision: p_descripcion has % chars, the maximum is 236', length(p_descripcion);
    END IF;
    IF length(p_usuario) > 255 THEN
        RAISE EXCEPTION 'auditoria_abrir_revision: p_usuario has % chars, the maximum is 255', length(p_usuario);
    END IF;

    -- Disabled users are accepted on purpose. The exact-case match wins if two correos differ only by case.
    SELECT correo INTO v_correo FROM public.usuario
     WHERE lower(correo) = lower(p_usuario)
     ORDER BY (correo = p_usuario) DESC, id_usuario
     LIMIT 1;
    IF v_correo IS NULL THEN
        RAISE EXCEPTION 'auditoria_abrir_revision: p_usuario % is not a registered usuario.correo', p_usuario;
    END IF;

    v_usuario_bd := CASE WHEN session_user = current_user THEN session_user::text
                         ELSE session_user::text || ' as ' || current_user::text END;

    INSERT INTO public.revinfo (fecha_revision, usuario, tipo_actor, descripcion, operacion_id, usuario_bd)
    VALUES (clock_timestamp() AT TIME ZONE 'America/Argentina/Buenos_Aires', v_correo, 'HUMAN',
            'Corrección manual: ' || p_descripcion, gen_random_uuid()::text, left(v_usuario_bd, 63))
    RETURNING rev INTO v_rev;
    RETURN v_rev;
END;
$$;

-- Copies the current state of one row of the base table of p_tabla_aud into p_tabla_aud (and into the
-- JOINED subclass _aud tables that have the row) for revision p_rev, and adds 1 to revinfo_resumen.
-- p_id is the primary key of the row, cast to the column type (so the index is used); the bigint overload below lets callers pass numeric ids. p_revtype: 0 ADD, 1 MOD, 2 DEL.
-- Columns are the intersection of base and _aud (minus rev/revtype), so it follows schema changes.
-- Returns the number of rows written (the root plus the subclass ones).
CREATE FUNCTION auditoria_registrar(p_rev integer, p_tabla_aud text, p_id text, p_revtype integer) RETURNS integer
LANGUAGE plpgsql SET search_path = public, pg_temp AS $$
DECLARE
    v_base text;
    v_subclasses text[] := CASE p_tabla_aud
        WHEN 'evento_academico_aud' THEN ARRAY['evento_recurrente', 'evento_unico']
        ELSE ARRAY[]::text[] END;
    v_target text;
    v_pk text;
    v_pk_type text;
    v_extra text;
    v_desc text;
    v_actor text;
    v_pk_count integer;
    v_cols text;
    v_written integer := 0;
    v_rows integer;
BEGIN
    IF p_rev IS NULL THEN
        RAISE EXCEPTION 'auditoria_registrar: p_rev is required';
    END IF;
    SELECT descripcion, tipo_actor INTO v_desc, v_actor FROM public.revinfo WHERE rev = p_rev;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'auditoria_registrar: revision % does not exist', p_rev;
    END IF;
    IF v_desc IS NULL OR v_desc NOT LIKE 'Corrección manual:%' OR v_actor <> 'HUMAN' THEN
        RAISE EXCEPTION 'auditoria_registrar: revision % is not a manual correction opened with auditoria_abrir_revision', p_rev;
    END IF;
    IF p_revtype IS NULL OR p_revtype NOT IN (0, 1, 2) THEN
        RAISE EXCEPTION 'auditoria_registrar: p_revtype must be 0, 1 or 2, got %', p_revtype;
    END IF;
    IF p_id IS NULL THEN
        RAISE EXCEPTION 'auditoria_registrar: p_id is required';
    END IF;
    IF p_tabla_aud IS NULL OR p_tabla_aud !~ '_aud$' THEN
        RAISE EXCEPTION 'auditoria_registrar: % is not an _aud table', p_tabla_aud;
    END IF;
    IF p_tabla_aud IN ('evento_recurrente_aud', 'evento_unico_aud') THEN
        RAISE EXCEPTION 'auditoria_registrar: register evento_academico_aud, which also writes the subclass tables';
    END IF;

    v_base := regexp_replace(p_tabla_aud, '_aud$', '');
    -- The root table first, then its JOINED subclasses (each one has its own _aud table).
    FOREACH v_target IN ARRAY (ARRAY[v_base] || v_subclasses) LOOP
        IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name = v_target)
           OR NOT EXISTS (SELECT 1 FROM information_schema.tables
                           WHERE table_schema = 'public' AND table_name = v_target || '_aud') THEN
            RAISE EXCEPTION 'auditoria_registrar: table % or % does not exist', v_target, v_target || '_aud';
        END IF;

        SELECT count(*), min(a.attname), min(format_type(a.atttypid, a.atttypmod))
          INTO v_pk_count, v_pk, v_pk_type
          FROM pg_index i
          JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY (i.indkey)
         WHERE i.indisprimary
           AND i.indrelid = format('public.%I', v_target)::regclass;
        IF v_pk_count <> 1 THEN
            RAISE EXCEPTION 'auditoria_registrar: % needs a single-column primary key (found %)', v_target, v_pk_count;
        END IF;

        SELECT string_agg(format('%I', c.column_name), ', ' ORDER BY c.ordinal_position) INTO v_cols
          FROM information_schema.columns c
          JOIN information_schema.columns b
            ON b.table_schema = c.table_schema AND b.table_name = v_target AND b.column_name = c.column_name
         WHERE c.table_schema = 'public' AND c.table_name = v_target || '_aud'
           AND c.column_name NOT IN ('rev', 'revtype');

        -- Cast p_id to the PK type up front so a bad id gets a clear message instead of the raw cast error.
        BEGIN
            EXECUTE format('SELECT $1::%s', v_pk_type) USING p_id;
        EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range THEN
            RAISE EXCEPTION 'auditoria_registrar: % id ''%'' is not a valid %', v_target, p_id, v_pk_type;
        END;

        -- _aud columns with no counterpart in the base table are not copied: tell the caller.
        SELECT string_agg(c.column_name, ', ' ORDER BY c.ordinal_position) INTO v_extra
          FROM information_schema.columns c
         WHERE c.table_schema = 'public' AND c.table_name = v_target || '_aud'
           AND c.column_name NOT IN ('rev', 'revtype')
           AND NOT EXISTS (SELECT 1 FROM information_schema.columns b
                            WHERE b.table_schema = 'public' AND b.table_name = v_target
                              AND b.column_name = c.column_name);
        IF v_extra IS NOT NULL THEN
            RAISE NOTICE 'auditoria_registrar: columns of % without a counterpart in %, left empty: %',
                         v_target || '_aud', v_target, v_extra;
        END IF;

        IF v_target = v_base THEN
            EXECUTE format('INSERT INTO public.%I (rev, revtype, %s) SELECT $1, $2, %s FROM public.%I WHERE %I = $3::%s',
                           v_target || '_aud', v_cols, v_cols, v_target, v_pk, v_pk_type)
                USING p_rev, p_revtype::smallint, p_id;
        ELSE
            -- A subclass table only has the row if the entity is of that subclass.
            EXECUTE format('INSERT INTO public.%I (rev, %s) SELECT $1, %s FROM public.%I WHERE %I = $2::%s',
                           v_target || '_aud', v_cols, v_cols, v_target, v_pk, v_pk_type)
                USING p_rev, p_id;
        END IF;
        GET DIAGNOSTICS v_rows = ROW_COUNT;

        IF v_target = v_base AND v_rows = 0 THEN
            RAISE EXCEPTION 'auditoria_registrar: % has no row with id %', v_base, p_id;
        END IF;
        v_written := v_written + v_rows;
    END LOOP;

    INSERT INTO public.revinfo_resumen AS r (rev, tabla_aud, revtype, cantidad)
    VALUES (p_rev, p_tabla_aud, p_revtype, 1)
    ON CONFLICT (rev, tabla_aud, revtype) DO UPDATE SET cantidad = r.cantidad + 1;

    RETURN v_written;
END;
$$;

-- Overload for numeric keys, so auditoria_registrar(:rev, 'ocurrencia_aud', id_ocurrencia, 1) works without casting.
CREATE FUNCTION auditoria_registrar(p_rev integer, p_tabla_aud text, p_id bigint, p_revtype integer) RETURNS integer
LANGUAGE sql SET search_path = public, pg_temp AS $$ SELECT public.auditoria_registrar(p_rev, p_tabla_aud, p_id::text, p_revtype) $$;
