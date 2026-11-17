UPDATE especialidad
SET nombre = NULL
WHERE hash_raw IS NULL
  AND eliminado_en IS NOT NULL
  AND nombre = 'Especialidad ' || codigo_especialidad;
