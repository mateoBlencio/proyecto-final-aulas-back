-- Deshace preparar.sql: borra las clases regulares posteriores al 30/11/2026 (con sus aulas, incluidas
-- las que se cambiaron durante la demo), vuelve a poner el 30/11/2026 como fin de los eventos y deja los
-- períodos ANUAL y 2.º cuatrimestre 2026 con el fin que tenían antes. Todo queda en la auditoría como
-- "Corrección manual".
-- No revierte los cambios de aula que se hayan hecho durante la demo en fechas hasta el 30/11.
--
-- Hay que correrlo ANTES del sync de SysAcad del domingo 29/11 a las 3:00.
--
-- Uso: psql -v usuario=<correo de un usuario de SIGA> < limpiar.sql

\set ON_ERROR_STOP on
\if :{?usuario}
\else
  \echo 'Falta -v usuario=<correo de un usuario de SIGA>'
  \quit
\endif

BEGIN;

CREATE TEMP TABLE demo_ocurrencia ON COMMIT DROP AS
SELECT o.id_ocurrencia, o.orden_aula
  FROM ocurrencia o
  JOIN evento_recurrente r ON r.id_evento_academico = o.id_evento_academico
 WHERE r.fecha_fin = DATE '2027-02-28'
   AND o.fecha > DATE '2026-11-30';

DO $$
DECLARE
    v_pedidos integer;
    v_sysacad integer;
BEGIN
    SELECT count(*) INTO v_pedidos
      FROM solicitud_item_asignacion s JOIN demo_ocurrencia d ON d.id_ocurrencia = s.id_ocurrencia;
    SELECT count(*) INTO v_sysacad
      FROM sysacad_horario_registro s JOIN demo_ocurrencia d ON d.id_ocurrencia = s.id_ocurrencia;
    IF v_pedidos > 0 OR v_sysacad > 0 THEN
        RAISE EXCEPTION 'Hay datos que apuntan a clases demo (% pedidos de aula, % registros de envío a SysAcad): revisalos a mano antes de limpiar',
            v_pedidos, v_sysacad;
    END IF;
END $$;

SELECT auditoria_abrir_revision('Limpieza de datos demo: las clases regulares vuelven a terminar el 30/11/2026', :'usuario') AS rev \gset

CREATE TEMP TABLE demo_asignacion ON COMMIT DROP AS
SELECT a.id_asignacion
  FROM asignacion_aula a
  JOIN demo_ocurrencia d ON d.id_ocurrencia = a.id_ocurrencia;

SELECT count(auditoria_registrar(:rev, 'asignacion_aula_aud', id_asignacion, 2)) AS asignaciones_borradas
  FROM demo_asignacion;
DELETE FROM asignacion_aula WHERE id_asignacion IN (SELECT id_asignacion FROM demo_asignacion);

SELECT count(auditoria_registrar(:rev, 'ocurrencia_aud', id_ocurrencia, 2)) AS ocurrencias_borradas
  FROM demo_ocurrencia;
DELETE FROM ocurrencia
 WHERE id_ocurrencia IN (SELECT id_ocurrencia FROM demo_ocurrencia WHERE orden_aula > 1);
DELETE FROM ocurrencia
 WHERE id_ocurrencia IN (SELECT id_ocurrencia FROM demo_ocurrencia);

CREATE TEMP TABLE demo_evento (id_evento_academico bigint) ON COMMIT DROP;
WITH u AS (
    UPDATE evento_recurrente
       SET fecha_fin = DATE '2026-11-30'
     WHERE fecha_fin = DATE '2027-02-28'
    RETURNING id_evento_academico)
INSERT INTO demo_evento SELECT id_evento_academico FROM u;

SELECT count(auditoria_registrar(:rev, 'evento_academico_aud', id_evento_academico, 1)) AS eventos_restaurados
  FROM demo_evento;

-- El fin anterior de cada período sale de su auditoría, de antes de la revisión de preparar.sql.
CREATE TEMP TABLE demo_periodo (id_periodo bigint) ON COMMIT DROP;
WITH rev_demo AS (
    SELECT max(rev) AS rev FROM revinfo
     WHERE descripcion = 'Corrección manual: Datos demo: clases regulares extendidas hasta el 28/02/2027'),
anterior AS (
    SELECT p.id_periodo,
           (SELECT a.fecha_fin FROM periodo_academico_aud a, rev_demo d
             WHERE a.id_periodo = p.id_periodo AND a.rev < d.rev
             ORDER BY a.rev DESC LIMIT 1) AS fecha_fin
      FROM periodo_academico p
     WHERE p.anio = 2026 AND p.cuatrimestre IN (0, 2) AND p.fecha_fin = DATE '2027-02-28'),
u AS (
    UPDATE periodo_academico p
       SET fecha_fin = coalesce(a.fecha_fin, DATE '2026-11-30')
      FROM anterior a
     WHERE p.id_periodo = a.id_periodo
    RETURNING p.id_periodo)
INSERT INTO demo_periodo SELECT id_periodo FROM u;

SELECT count(auditoria_registrar(:rev, 'periodo_academico_aud', id_periodo, 1)) AS periodos_restaurados
  FROM demo_periodo;

COMMIT;

\echo 'Listo. verificar.sql tiene que mostrar 0 eventos extendidos y 0 clases después del 30/11.'
