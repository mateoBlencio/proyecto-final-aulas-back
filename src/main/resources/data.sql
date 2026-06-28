-- Seed de datos de referencia requeridos para el funcionamiento del sistema.
-- Spring Boot ejecuta este archivo automáticamente solo cuando
-- spring.sql.init.mode=always (activo únicamente en el perfil dev-local).
INSERT INTO tipo_aula (descripcion, eliminado)
SELECT 'Normal', false
WHERE NOT EXISTS (SELECT 1 FROM tipo_aula WHERE descripcion = 'Normal');