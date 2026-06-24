INSERT INTO tipo_aula (descripcion, eliminado)
SELECT 'Aula Standard', false
WHERE NOT EXISTS (SELECT 1 FROM tipo_aula WHERE descripcion = 'Aula Standard');