-- Operacion de auditoria que causo esta revision (por ejemplo, la liberacion de un aula
-- disparada por una ocurrencia desocupada). Null si la operacion no tiene causa.
ALTER TABLE revinfo ADD COLUMN operacion_padre_id varchar(36);
CREATE INDEX idx_revinfo_operacion_padre_id ON revinfo (operacion_padre_id);
