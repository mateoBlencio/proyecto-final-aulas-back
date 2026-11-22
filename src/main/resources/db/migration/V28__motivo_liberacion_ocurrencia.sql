-- Motivo de liberación de aula por ocurrencia (plans/desasignacion).

ALTER TABLE ocurrencia ADD COLUMN motivo_liberacion varchar(40);
ALTER TABLE ocurrencia ADD COLUMN nota_liberacion   varchar(255);
ALTER TABLE ocurrencia ADD COLUMN id_aula_anterior  bigint;

ALTER TABLE ocurrencia ADD CONSTRAINT fk_ocurrencia_aula_anterior
    FOREIGN KEY (id_aula_anterior) REFERENCES aula(id_aula);

-- Las ocurrencias ya liberadas antes de esta migración no dejaron registro de por qué.
UPDATE ocurrencia
   SET motivo_liberacion = CASE WHEN id_ocurrencia_principal IS NOT NULL
                                THEN 'MIRROR_RETIRED' ELSE 'OTHER' END
 WHERE estado = 'ROOM_RELEASED';

ALTER TABLE ocurrencia ADD CONSTRAINT chk_ocurrencia_motivo_liberacion
    CHECK (motivo_liberacion IS NULL OR motivo_liberacion IN
        ('VIRTUAL_CLASS', 'CANCELLED', 'MIRROR_RETIRED', 'OTHER'));

ALTER TABLE ocurrencia ADD CONSTRAINT chk_ocurrencia_motivo_coherente
    CHECK ((estado = 'ROOM_RELEASED' AND motivo_liberacion IS NOT NULL)
        OR (estado = 'NEEDS_ROOM' AND motivo_liberacion IS NULL AND nota_liberacion IS NULL));

-- Envers. Sin checks ni FK: una tabla _aud guarda estados intermedios y revtype=2 escribe nulos.
ALTER TABLE ocurrencia_aud ADD COLUMN motivo_liberacion varchar(40);
ALTER TABLE ocurrencia_aud ADD COLUMN nota_liberacion   varchar(255);
ALTER TABLE ocurrencia_aud ADD COLUMN id_aula_anterior  bigint;

-- El archivado copia con SELECT a.* posicional: las dos tablas _aud tienen que quedar con las
-- mismas columnas en el mismo orden (ver V19 y AuditArchiveRepository).
ALTER TABLE archivo.ocurrencia_aud ADD COLUMN motivo_liberacion varchar(40);
ALTER TABLE archivo.ocurrencia_aud ADD COLUMN nota_liberacion   varchar(255);
ALTER TABLE archivo.ocurrencia_aud ADD COLUMN id_aula_anterior  bigint;
