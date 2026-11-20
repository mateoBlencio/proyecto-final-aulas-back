ALTER TABLE actividad_edificio ADD COLUMN tipo varchar(40) NOT NULL DEFAULT 'ALLOCATION_CHANGE';
ALTER TABLE actividad_edificio ADD COLUMN solicitud jsonb;
