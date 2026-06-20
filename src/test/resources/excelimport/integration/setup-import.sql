TRUNCATE TABLE periodo_academico, materia_comision, comision, materia, plan_estudio,
           especialidad, franja_horaria, asignacion_aula, aula, tipo_aula, edificio
    RESTART IDENTITY CASCADE;

INSERT INTO edificio (id_edificio, nombre, cantidad_pisos, activo, eliminado)
VALUES (1, 'Edif. Dr. Gallardo', 5, true, false);

INSERT INTO tipo_aula (id_tipo_aula, descripcion, eliminado)
VALUES (1, 'aula', false);

INSERT INTO aula (id_aula, num_aula, piso, capacidad, disponible, eliminado, id_edificio, id_tipo_aula)
VALUES (1, '513', 5, 50, true, false, 1, 1);

INSERT INTO especialidad (id_especialidad, codigo_especialidad, nombre, eliminado)
VALUES (1, 31, 'Ingeniería Civil', false);

SELECT setval('edificio_id_edificio_seq', 1);
SELECT setval('tipo_aula_id_tipo_aula_seq', 1);
SELECT setval('aula_id_aula_seq', 1);
SELECT setval('especialidad_id_especialidad_seq', 1);
