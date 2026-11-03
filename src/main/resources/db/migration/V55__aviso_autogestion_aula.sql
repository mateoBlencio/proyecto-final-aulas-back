CREATE TABLE aviso_autogestion_aula (
    id_ocurrencia bigint PRIMARY KEY,
    id_aula bigint,
    actualizado_en timestamp with time zone NOT NULL
);
