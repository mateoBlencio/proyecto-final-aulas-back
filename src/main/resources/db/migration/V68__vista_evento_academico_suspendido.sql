CREATE VIEW evento_academico_suspendido AS
SELECT e.id_evento_academico,
       CAST((LEAST(m.eliminado_en, p.eliminado_en, es.eliminado_en, c.eliminado_en, mc.eliminado_en)
             AT TIME ZONE 'UTC') AT TIME ZONE 'America/Argentina/Buenos_Aires' AS date) AS fecha_baja
FROM evento_academico e
LEFT JOIN materia m ON m.id_materia = e.id_materia
LEFT JOIN plan_estudio p ON p.id_plan = m.id_plan
LEFT JOIN especialidad es ON es.id_especialidad = p.id_especialidad
LEFT JOIN comision c ON c.id_comision = e.id_comision
LEFT JOIN materia_comision mc ON mc.id_materia = e.id_materia AND mc.id_comision = e.id_comision
WHERE m.eliminado_en IS NOT NULL
   OR p.eliminado_en IS NOT NULL
   OR es.eliminado_en IS NOT NULL
   OR c.eliminado_en IS NOT NULL
   OR mc.eliminado_en IS NOT NULL;
