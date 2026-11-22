-- Extiende hasta el 28/02/2027 todas las clases regulares que hoy terminan el 30/11/2026, como si se
-- hubiera ampliado el período académico. Cada clase repite su última semana: mismas órdenes de aula
-- (principal y espejos) y la misma aula. Como el resto del año, genera clases también en feriados.
-- No asigna aula en una fecha donde esa aula ya está ocupada por algo que existía antes (por ejemplo,
-- una mesa de examen). Todo queda en la auditoría como "Corrección manual". Se deshace con limpiar.sql.
--
-- Uso: psql -v usuario=<correo de un usuario de SIGA> < preparar.sql

\set ON_ERROR_STOP on
\if :{?usuario}
\else
  \echo 'Falta -v usuario=<correo de un usuario de SIGA>'
  \quit
\endif

BEGIN;

DO $$
BEGIN
    IF coalesce((SELECT valor FROM configuracion WHERE clave = 'sysacad.write.mode'), '(sin valor)') <> 'OFF' THEN
        RAISE EXCEPTION 'sysacad.write.mode no está en OFF: ponelo en OFF desde Configuración antes de cargar los datos demo';
    END IF;
    IF EXISTS (SELECT 1
                 FROM ocurrencia o
                 JOIN evento_recurrente r ON r.id_evento_academico = o.id_evento_academico
                WHERE o.fecha > DATE '2026-11-30') THEN
        RAISE EXCEPTION 'Ya hay clases regulares después del 30/11: los datos demo ya están cargados. Corré limpiar.sql antes de volver a cargarlos';
    END IF;
END $$;

SELECT auditoria_abrir_revision('Datos demo: clases regulares extendidas hasta el 28/02/2027', :'usuario') AS rev \gset

CREATE TEMP TABLE demo_evento ON COMMIT DROP AS
SELECT r.id_evento_academico, max(o.fecha) AS fecha_base
  FROM evento_recurrente r
  JOIN evento_academico e ON e.id_evento_academico = r.id_evento_academico
  JOIN ocurrencia o ON o.id_evento_academico = r.id_evento_academico
 WHERE r.fecha_fin = DATE '2026-11-30'
   AND e.habilitado_siga
   AND o.fecha BETWEEN DATE '2026-11-23' AND DATE '2026-11-30'
 GROUP BY r.id_evento_academico;

-- El aula de la última semana; si esa fecha quedó sin aula, la más usada en noviembre.
CREATE TEMP TABLE demo_slot ON COMMIT DROP AS
SELECT d.id_evento_academico,
       d.fecha_base,
       o.orden_aula,
       o.id_ocurrencia_principal IS NOT NULL AS es_espejo,
       coalesce(
           (SELECT a.id_aula
              FROM asignacion_aula a
              JOIN aula au ON au.id_aula = a.id_aula AND au.eliminado_en IS NULL
             WHERE a.id_ocurrencia = o.id_ocurrencia),
           (SELECT mode() WITHIN GROUP (ORDER BY a.id_aula)
              FROM ocurrencia o2
              JOIN asignacion_aula a ON a.id_ocurrencia = o2.id_ocurrencia
              JOIN aula au ON au.id_aula = a.id_aula AND au.eliminado_en IS NULL
             WHERE o2.id_evento_academico = d.id_evento_academico
               AND o2.orden_aula = o.orden_aula
               AND o2.fecha BETWEEN DATE '2026-11-01' AND DATE '2026-11-30')) AS id_aula
  FROM demo_evento d
  JOIN ocurrencia o ON o.id_evento_academico = d.id_evento_academico AND o.fecha = d.fecha_base;

UPDATE evento_recurrente r
   SET fecha_fin = DATE '2027-02-28'
  FROM demo_evento d
 WHERE r.id_evento_academico = d.id_evento_academico;

SELECT count(auditoria_registrar(:rev, 'evento_academico_aud', id_evento_academico, 1)) AS eventos_extendidos
  FROM demo_evento;

CREATE TEMP TABLE demo_periodo (id_periodo bigint) ON COMMIT DROP;
WITH u AS (
    UPDATE periodo_academico
       SET fecha_fin = DATE '2027-02-28'
     WHERE anio = 2026 AND cuatrimestre IN (0, 2)
       AND fecha_fin < DATE '2027-02-28'
       AND eliminado_en IS NULL
    RETURNING id_periodo)
INSERT INTO demo_periodo SELECT id_periodo FROM u;

SELECT count(auditoria_registrar(:rev, 'periodo_academico_aud', id_periodo, 1)) AS periodos_extendidos
  FROM demo_periodo;

CREATE TEMP TABLE demo_ocurrencia (
    id_ocurrencia bigint,
    id_evento_academico bigint,
    fecha date,
    orden_aula integer
) ON COMMIT DROP;

WITH ins AS (
    INSERT INTO ocurrencia (id_evento_academico, fecha, estado, orden_aula, creado_en, actualizado_en)
    SELECT s.id_evento_academico, g.fecha, 'NEEDS_ROOM', s.orden_aula, now(), now()
      FROM demo_slot s
     CROSS JOIN LATERAL (
            SELECT generate_series(s.fecha_base + 7, DATE '2027-02-28', interval '7 days')::date AS fecha) g
     WHERE NOT s.es_espejo
    RETURNING id_ocurrencia, id_evento_academico, fecha, orden_aula)
INSERT INTO demo_ocurrencia SELECT * FROM ins;

WITH principal AS (
    SELECT DISTINCT ON (id_evento_academico, fecha) id_ocurrencia, id_evento_academico, fecha
      FROM demo_ocurrencia
     ORDER BY id_evento_academico, fecha, orden_aula),
ins AS (
    INSERT INTO ocurrencia (id_evento_academico, fecha, estado, orden_aula, id_ocurrencia_principal,
                            creado_en, actualizado_en)
    SELECT s.id_evento_academico, p.fecha, 'NEEDS_ROOM', s.orden_aula, p.id_ocurrencia, now(), now()
      FROM demo_slot s
      JOIN principal p ON p.id_evento_academico = s.id_evento_academico
     WHERE s.es_espejo
    RETURNING id_ocurrencia, id_evento_academico, fecha, orden_aula)
INSERT INTO demo_ocurrencia SELECT * FROM ins;

SELECT count(auditoria_registrar(:rev, 'ocurrencia_aud', id_ocurrencia, 0)) AS ocurrencias_creadas
  FROM demo_ocurrencia;

CREATE TEMP TABLE demo_asignacion (id_asignacion bigint) ON COMMIT DROP;
WITH ins AS (
    INSERT INTO asignacion_aula (id_ocurrencia, id_aula, origen, creado_en, actualizado_en)
    SELECT o.id_ocurrencia, s.id_aula, 'MANUAL', now(), now()
      FROM demo_ocurrencia o
      JOIN demo_slot s ON s.id_evento_academico = o.id_evento_academico AND s.orden_aula = o.orden_aula
      JOIN evento_academico e ON e.id_evento_academico = o.id_evento_academico
     WHERE s.id_aula IS NOT NULL
       AND NOT EXISTS (
            SELECT 1
              FROM asignacion_aula a2
              JOIN ocurrencia o2 ON o2.id_ocurrencia = a2.id_ocurrencia
              JOIN evento_academico e2 ON e2.id_evento_academico = o2.id_evento_academico
             WHERE a2.id_aula = s.id_aula
               AND o2.fecha = o.fecha
               AND e2.hora_inicio < e.hora_inicio + make_interval(mins => e.duracion_minutos)
               AND e.hora_inicio < e2.hora_inicio + make_interval(mins => e2.duracion_minutos))
    RETURNING id_asignacion)
INSERT INTO demo_asignacion SELECT id_asignacion FROM ins;

SELECT count(auditoria_registrar(:rev, 'asignacion_aula_aud', id_asignacion, 0)) AS asignaciones_creadas
  FROM demo_asignacion;

COMMIT;

\echo 'Listo. Corré verificar.sql para ver el resultado y elegir las clases de la demo.'
