TRUNCATE aula, tipo_aula, edificio RESTART IDENTITY CASCADE;

INSERT INTO edificio (id_edificio, nombre, cantidad_pisos, activo, eliminado)
VALUES (1, 'Central', 5, true, false);
INSERT INTO edificio (id_edificio, nombre, cantidad_pisos, activo, eliminado)
VALUES (2, 'Possetto', 3, true, false);

INSERT INTO tipo_aula (id_tipo, desc_tipo, eliminado)
VALUES (1, 'aula', false);
INSERT INTO tipo_aula (id_tipo, desc_tipo, eliminado)
VALUES (2, 'laboratorio', false);

INSERT INTO aula (id_aula, num_aula, piso, capacidad, disponible, eliminado, id_edificio, id_tipo_aula)
VALUES (1, '101', 2, 30, true, false, 1, 1);
INSERT INTO aula (id_aula, num_aula, piso, capacidad, disponible, eliminado, id_edificio, id_tipo_aula)
VALUES (2, '102', 1, 25, true, false, 1, 2);

INSERT INTO aula (id_aula, num_aula, piso, capacidad, disponible, eliminado, id_edificio, id_tipo_aula)
VALUES (3, '201', 1, 20, false, true, 2, 2);

SELECT setval('edificio_id_edificio_seq', 2);
SELECT setval('tipo_aula_id_tipo_seq', 2);
SELECT setval('aula_id_aula_seq', 3);
