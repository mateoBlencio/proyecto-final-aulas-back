-- Aula que ocupaba la clase antes del primer assign de un cambio de aula. Se completa una sola
-- vez (ver RoomRequestItem.rememberPreviousClassroom), para que el mail de "pedido resuelto"
-- pueda mostrar la línea "Aula anterior" en ONE_TIME_ROOM_CHANGE y REGULAR_ROOM_CHANGE.

ALTER TABLE solicitud_aula_item ADD COLUMN IF NOT EXISTS id_aula_anterior bigint;
ALTER TABLE solicitud_aula_item_aud ADD COLUMN IF NOT EXISTS id_aula_anterior bigint;
ALTER TABLE solicitud_aula_item ADD CONSTRAINT fk_solicitud_item_aula_anterior
    FOREIGN KEY (id_aula_anterior) REFERENCES aula(id_aula);
