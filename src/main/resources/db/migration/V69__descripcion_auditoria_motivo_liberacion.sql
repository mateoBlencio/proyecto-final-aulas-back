DO $$
DECLARE
    v_tabla text;
    v_motivo text[];
    v_motivos text[][] := ARRAY[
        ['VIRTUAL_CLASS', 'Clase virtual'],
        ['CANCELLED', 'Cancelada'],
        ['MIRROR_RETIRED', 'Espejo retirado'],
        ['OTHER', 'Otro'],
        ['CLASSROOM_DEACTIVATED', 'Aula o edificio deshabilitado']];
BEGIN
    FOREACH v_tabla IN ARRAY ARRAY['public.revinfo', 'archivo.revinfo'] LOOP
        FOREACH v_motivo SLICE 1 IN ARRAY v_motivos LOOP
            EXECUTE format(
                'UPDATE %s SET descripcion = replace(descripcion, %L, %L) '
                || 'WHERE descripcion LIKE %L',
                v_tabla, '(' || v_motivo[1] || ')', '(' || v_motivo[2] || ')', 'Liberación de %(' || v_motivo[1] || ')');
        END LOOP;
    END LOOP;
END $$;
