TRUNCATE TABLE asignacion_aula, ocurrencia, evento_recurrente_fecha_excluida,
               evento_recurrente, evento_unico_academico, evento_academico,
               comision, periodo_academico, materia, plan_estudio,
               especialidad, aula, tipo_aula, edificio
    RESTART IDENTITY CASCADE;

INSERT INTO edificio (id_edificio, nombre, cantidad_pisos, activo, eliminado)
VALUES (1, 'Edif. Central', 5, true, false);

INSERT INTO tipo_aula (id_tipo_aula, descripcion, eliminado)
VALUES (1, 'aula', false);

INSERT INTO aula (id_aula, num_aula, piso, capacidad, disponible, eliminado, id_edificio, id_tipo_aula)
VALUES (1, '101', 1, 40, true, false, 1, 1);

INSERT INTO especialidad (id_especialidad, codigo_especialidad, nombre, eliminado)
VALUES (1, 7, 'Ingeniería en Sistemas', false);

INSERT INTO plan_estudio (id_plan, codigo_plan, id_especialidad, eliminado)
VALUES (1, 2008, 1, false);

INSERT INTO materia (id_materia, codigo_materia, nombre, id_plan, dictado, eliminado)
VALUES (1, 100, 'Programación I', 1, 'Anual', false);

INSERT INTO periodo_academico (id_periodo, anio, cuatrimestre, activo)
VALUES (1, 2024, 1, true);

INSERT INTO comision (id_comision, codigo_curso, numero_comision, anio_nivel, id_periodo, eliminado)
VALUES (1, 'K1234', 1, 1, 1, false);

INSERT INTO evento_academico (id_evento_academico, tipo_evento, cantidad_inscriptos, hora_inicio, duracion_minutos)
VALUES (1, 'RECURRING', 30, '08:00:00', 90);

INSERT INTO evento_recurrente (id_evento_academico, dia_semana, fecha_inicio, fecha_fin, id_materia, id_comision)
VALUES (1, 'MONDAY', CURRENT_DATE - 60, CURRENT_DATE + 60, 1, 1);

-- ocurrencia 1: futura, SCHEDULED -> debe aparecer
-- ocurrencia 2: futura, ASSIGNED (con fila en asignacion_aula) -> no debe aparecer
-- ocurrencia 3: futura, CANCELLED -> no debe aparecer
-- ocurrencia 4: futura, SUSPENDED -> no debe aparecer
-- ocurrencia 5: pasada, SCHEDULED -> solo aparece si "from" incluye el pasado
-- ocurrencia 6: futura (mas alla de +10 dias), SCHEDULED -> agrupa junto a la 1, fuera de rango ?to
INSERT INTO ocurrencia (id_ocurrencia, id_evento_academico, fecha, estado) VALUES
    (1, 1, CURRENT_DATE + 7, 'SCHEDULED'),
    (2, 1, CURRENT_DATE + 14, 'ASSIGNED'),
    (3, 1, CURRENT_DATE + 21, 'CANCELLED'),
    (4, 1, CURRENT_DATE + 28, 'SUSPENDED'),
    (5, 1, CURRENT_DATE - 7, 'SCHEDULED'),
    (6, 1, CURRENT_DATE + 35, 'SCHEDULED');

INSERT INTO asignacion_aula (id_asignacion, id_ocurrencia, id_aula, origen, fecha_creacion)
VALUES (1, 2, 1, 'MANUAL', now());

SELECT setval('edificio_id_edificio_seq', 1);
SELECT setval('tipo_aula_id_tipo_aula_seq', 1);
SELECT setval('aula_id_aula_seq', 1);
SELECT setval('especialidad_id_especialidad_seq', 1);
SELECT setval('plan_estudio_id_plan_seq', 1);
SELECT setval('materia_id_materia_seq', 1);
SELECT setval('periodo_academico_id_periodo_seq', 1);
SELECT setval('comision_id_comision_seq', 1);
SELECT setval('evento_academico_id_evento_academico_seq', 1);
SELECT setval('ocurrencia_id_ocurrencia_seq', 6);
SELECT setval('asignacion_aula_id_asignacion_seq', 1);