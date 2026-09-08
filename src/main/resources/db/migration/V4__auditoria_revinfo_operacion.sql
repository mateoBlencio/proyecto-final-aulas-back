-- Registro de auditoría unificado: descripción de la revisión y agrupación por operación de negocio.
-- Envers escribe estas columnas desde la entidad SigaRevision (módulo audit).
-- Numeración V4 según el plan maestro (V3 = RBAC, V5 = periodo académico); en develop
-- todavía sólo existen V1/V2, y Flyway no exige numeración contigua.
-- IF NOT EXISTS: algunos ambientes ya aplicaron el script manual previo (db/manual) o ddl-auto.

ALTER TABLE revinfo ADD COLUMN IF NOT EXISTS descripcion varchar(255);
ALTER TABLE revinfo ADD COLUMN IF NOT EXISTS operacion_id varchar(36);

CREATE INDEX IF NOT EXISTS idx_revinfo_operacion_id ON revinfo (operacion_id);
