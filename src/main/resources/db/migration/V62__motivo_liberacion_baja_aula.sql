ALTER TABLE ocurrencia DROP CONSTRAINT chk_ocurrencia_motivo_liberacion;
ALTER TABLE ocurrencia ADD CONSTRAINT chk_ocurrencia_motivo_liberacion
    CHECK (motivo_liberacion IS NULL OR motivo_liberacion IN
        ('VIRTUAL_CLASS', 'CANCELLED', 'MIRROR_RETIRED', 'OTHER', 'CLASSROOM_DEACTIVATED'));
