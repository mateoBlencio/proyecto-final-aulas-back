-- Summary of rows per revision, root _aud table and revtype, kept by SigaRevisionListener.entityChanged.
-- The audit log listing reads it instead of scanning every _aud table.
-- Writes by direct SQL do not feed it. The JOINED subclass tables (evento_recurrente_aud, evento_unico_aud)
-- are not counted: the row of the root (evento_academico_aud) already counts the revision.
CREATE TABLE revinfo_resumen (
    rev integer NOT NULL REFERENCES revinfo (rev),
    tabla_aud varchar(63) NOT NULL,
    revtype smallint NOT NULL,
    cantidad integer NOT NULL,
    PRIMARY KEY (rev, tabla_aud, revtype)
);
CREATE INDEX idx_revinfo_resumen_tabla_revtype ON revinfo_resumen (tabla_aud, revtype, rev);

INSERT INTO revinfo_resumen (rev, tabla_aud, revtype, cantidad)
SELECT t.rev, t.tabla_aud, t.revtype, t.cantidad FROM (
    SELECT rev, 'asignacion_aula_aud' AS tabla_aud, revtype, count(*) AS cantidad FROM asignacion_aula_aud GROUP BY rev, revtype
    UNION ALL
    SELECT rev, 'usuario_aud', revtype, count(*) FROM usuario_aud GROUP BY rev, revtype
    UNION ALL
    SELECT rev, 'usuario_rol_aud', revtype, count(*) FROM usuario_rol_aud GROUP BY rev, revtype
    UNION ALL
    SELECT rev, 'evento_academico_aud', revtype, count(*) FROM evento_academico_aud GROUP BY rev, revtype
    UNION ALL
    SELECT rev, 'ocurrencia_aud', revtype, count(*) FROM ocurrencia_aud GROUP BY rev, revtype
    UNION ALL
    SELECT rev, 'solicitud_aula_aud', revtype, count(*) FROM solicitud_aula_aud GROUP BY rev, revtype
    UNION ALL
    SELECT rev, 'solicitud_aula_item_aud', revtype, count(*) FROM solicitud_aula_item_aud GROUP BY rev, revtype
    UNION ALL
    SELECT rev, 'solicitud_aula_preferencia_aud', revtype, count(*) FROM solicitud_aula_preferencia_aud GROUP BY rev, revtype
    UNION ALL
    SELECT rev, 'solicitud_item_asignacion_aud', revtype, count(*) FROM solicitud_item_asignacion_aud GROUP BY rev, revtype
    UNION ALL
    SELECT rev, 'configuracion_aud', revtype, count(*) FROM configuracion_aud GROUP BY rev, revtype
) t
WHERE t.rev IN (SELECT rev FROM revinfo);
