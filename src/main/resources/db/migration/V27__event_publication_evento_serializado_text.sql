-- ClassroomMovements serializado supera los 255 caracteres de la columna creada en la baseline.
ALTER TABLE event_publication ALTER COLUMN serialized_event TYPE text;
