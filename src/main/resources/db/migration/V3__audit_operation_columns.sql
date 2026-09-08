-- Registro de auditoría unificado: descripción de la revisión y agrupación por operación de negocio.
-- Envers escribe estas columnas desde la entidad SigaRevision.

ALTER TABLE revinfo ADD COLUMN IF NOT EXISTS descripcion varchar(255);
ALTER TABLE revinfo ADD COLUMN IF NOT EXISTS operacion_id varchar(36);

CREATE INDEX IF NOT EXISTS idx_revinfo_operacion_id ON revinfo (operacion_id);
