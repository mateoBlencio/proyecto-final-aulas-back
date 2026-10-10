-- Holidays mirrored from the Sysacad "Feriados" view. Full days only.
CREATE TABLE feriado (
    fecha date PRIMARY KEY,
    descripcion varchar(255),
    sincronizado_en timestamp(6) NOT NULL
);
