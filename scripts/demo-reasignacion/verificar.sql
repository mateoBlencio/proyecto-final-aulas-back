-- Solo lectura. Resume los datos demo y propone clases y aulas para probar.sh y para el guion.
-- Uso: psql < verificar.sql

\set ON_ERROR_STOP on
\pset footer off

\echo '== Resumen'
SELECT (SELECT count(*) FROM evento_recurrente WHERE fecha_fin = DATE '2027-02-28') AS eventos_extendidos,
       count(*) AS clases_despues_del_30_11,
       count(a.id_asignacion) AS con_aula,
       count(*) - count(a.id_asignacion) AS sin_aula
  FROM ocurrencia o
  JOIN evento_recurrente r ON r.id_evento_academico = o.id_evento_academico
  LEFT JOIN asignacion_aula a ON a.id_ocurrencia = o.id_ocurrencia
 WHERE o.fecha > DATE '2026-11-30';

\echo '== Períodos 2026'
SELECT id_periodo, cuatrimestre, fecha_inicio, fecha_fin
  FROM periodo_academico WHERE anio = 2026 AND eliminado_en IS NULL ORDER BY cuatrimestre;

\echo '== Semanas futuras por clase (solo aula principal)'
SELECT min(n) AS minimo, max(n) AS maximo, round(avg(n), 1) AS promedio
  FROM (SELECT count(*) AS n
          FROM ocurrencia o
          JOIN evento_recurrente r ON r.id_evento_academico = o.id_evento_academico
         WHERE r.fecha_fin = DATE '2027-02-28' AND o.fecha > DATE '2026-11-30' AND o.id_ocurrencia_principal IS NULL
         GROUP BY o.id_evento_academico, o.orden_aula) t;

\echo '== Choques de aula por semana: la semana real del 23/11 contra el promedio de las semanas demo'
WITH x AS (
    SELECT a.id_aula, o.fecha, o.id_evento_academico,
           e.hora_inicio AS inicio, e.hora_inicio + make_interval(mins => e.duracion_minutos) AS fin
      FROM asignacion_aula a
      JOIN ocurrencia o ON o.id_ocurrencia = a.id_ocurrencia
      JOIN evento_academico e ON e.id_evento_academico = o.id_evento_academico
     WHERE o.fecha >= DATE '2026-11-23'),
choque AS (
    SELECT x1.fecha
      FROM x x1
      JOIN x x2 ON x1.id_aula = x2.id_aula AND x1.fecha = x2.fecha
               AND x1.id_evento_academico < x2.id_evento_academico
               AND x1.inicio < x2.fin AND x2.inicio < x1.fin)
SELECT count(*) FILTER (WHERE fecha <= DATE '2026-11-29') AS semana_real_23_11,
       round(count(*) FILTER (WHERE fecha > DATE '2026-11-30')
             / greatest(1, (DATE '2027-02-28' - DATE '2026-12-01') / 7.0), 1) AS promedio_semana_demo
  FROM choque;

\echo '== Feriados en el rango y clases que caen ese día'
SELECT f.fecha, f.descripcion,
       (SELECT count(*)
          FROM ocurrencia o
          JOIN evento_recurrente r ON r.id_evento_academico = o.id_evento_academico
         WHERE o.fecha = f.fecha) AS clases
  FROM feriado f
 WHERE f.fecha BETWEEN DATE '2026-12-01' AND DATE '2027-02-28' AND f.eliminado_en IS NULL
 ORDER BY f.fecha;

\echo '== Clases con dos días o más (para el guion)'
SELECT m.nombre AS materia, c.codigo_curso AS comision,
       string_agg(r.dia_semana || ' ' || to_char(e.hora_inicio, 'HH24:MI') || ' (evento ' || e.id_evento_academico || ')', ', '
                  ORDER BY r.dia_semana) AS clases
  FROM evento_academico e
  JOIN evento_recurrente r ON r.id_evento_academico = e.id_evento_academico
  JOIN materia m ON m.id_materia = e.id_materia
  JOIN comision c ON c.id_comision = e.id_comision
 WHERE r.fecha_fin = DATE '2027-02-28'
 GROUP BY m.nombre, c.codigo_curso
HAVING count(DISTINCT r.dia_semana) >= 2
 ORDER BY m.nombre LIMIT 5;

\echo '== Candidatos para probar.sh: <evento> <aula actual> <aula libre> <aula ocupada>'
\echo '   (aula ocupada: choca en todas las fechas por más del margen allocation.maxOverlapMinutes)'
WITH clase AS (
    SELECT e.id_evento_academico, e.cantidad_inscriptos, e.hora_inicio,
           e.hora_inicio + make_interval(mins => e.duracion_minutos) AS fin,
           r.dia_semana, min(a.id_aula) AS id_aula, au.id_edificio
      FROM evento_academico e
      JOIN evento_recurrente r ON r.id_evento_academico = e.id_evento_academico
      JOIN ocurrencia o ON o.id_evento_academico = e.id_evento_academico AND o.fecha > DATE '2026-11-30'
      LEFT JOIN asignacion_aula a ON a.id_ocurrencia = o.id_ocurrencia
      LEFT JOIN aula au ON au.id_aula = a.id_aula
     WHERE r.fecha_fin = DATE '2027-02-28'
     GROUP BY e.id_evento_academico, e.cantidad_inscriptos, e.hora_inicio, e.duracion_minutos, r.dia_semana, au.id_edificio
    HAVING max(o.orden_aula) = 1
       AND count(a.id_asignacion) = count(*)
       AND count(DISTINCT a.id_aula) = 1
     LIMIT 200),
fechas AS (
    SELECT o.id_evento_academico, o.fecha
      FROM ocurrencia o JOIN clase c ON c.id_evento_academico = o.id_evento_academico
     WHERE o.fecha > DATE '2026-11-30'),
ocupacion AS (
    SELECT a.id_aula, o.fecha, e.hora_inicio AS inicio,
           e.hora_inicio + make_interval(mins => e.duracion_minutos) AS fin
      FROM asignacion_aula a
      JOIN ocurrencia o ON o.id_ocurrencia = a.id_ocurrencia
      JOIN evento_academico e ON e.id_evento_academico = o.id_evento_academico
     WHERE o.fecha > DATE '2026-11-30')
SELECT c.id_evento_academico AS evento, c.id_aula AS aula_actual, libre.id_aula AS aula_libre,
       ocupada.id_aula AS aula_ocupada, c.dia_semana, to_char(c.hora_inicio, 'HH24:MI') AS hora
  FROM clase c
  CROSS JOIN LATERAL (
        SELECT au.id_aula
          FROM aula au
          JOIN edificio ed ON ed.id_edificio = au.id_edificio AND ed.eliminado_en IS NULL
         WHERE au.id_edificio = c.id_edificio
           AND au.id_aula <> c.id_aula
           AND au.eliminado_en IS NULL
           AND (au.vigente_sysacad OR au.hash_raw IS NULL)
           AND au.capacidad >= c.cantidad_inscriptos
           AND NOT EXISTS (
                SELECT 1 FROM ocupacion x JOIN fechas f ON f.fecha = x.fecha
                 WHERE f.id_evento_academico = c.id_evento_academico
                   AND x.id_aula = au.id_aula
                   AND x.inicio < c.fin AND c.hora_inicio < x.fin)
         ORDER BY au.capacidad
         LIMIT 1) libre
  CROSS JOIN LATERAL (
        SELECT x.id_aula
          FROM ocupacion x JOIN fechas f ON f.fecha = x.fecha
         WHERE f.id_evento_academico = c.id_evento_academico
           AND x.id_aula <> c.id_aula
           AND least(x.fin, c.fin) - greatest(x.inicio, c.hora_inicio) > make_interval(mins => (
                SELECT coalesce(max(valor::int), 40) FROM configuracion WHERE clave = 'allocation.maxOverlapMinutes'))
         GROUP BY x.id_aula
        HAVING count(*) = (SELECT count(*) FROM fechas f2 WHERE f2.id_evento_academico = c.id_evento_academico)
         LIMIT 1) ocupada
 LIMIT 5;
