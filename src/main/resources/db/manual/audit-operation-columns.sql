-- Registro de auditoría unificado: descripción de la revisión y agrupación por operación de negocio.
-- El schema de producción se provee fuera de la app (spring.jpa.hibernate.ddl-auto=validate, sin Flyway),
-- por lo que este script debe aplicarse manualmente antes de desplegar el módulo `audit`.
-- En dev (ddl-auto=update) y test (create-drop) estas columnas se crean solas desde la entidad SigaRevision.

ALTER TABLE revinfo ADD COLUMN IF NOT EXISTS descripcion varchar(255);
ALTER TABLE revinfo ADD COLUMN IF NOT EXISTS operacion_id varchar(36);

CREATE INDEX IF NOT EXISTS idx_revinfo_operacion_id ON revinfo (operacion_id);
