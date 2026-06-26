INSERT INTO tipo_aula (descripcion, eliminado)
SELECT 'Aula', false
WHERE NOT EXISTS (SELECT 1 FROM tipo_aula WHERE descripcion = 'Aula Normal');