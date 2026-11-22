-- Movimientos de aula agrupados por edificio y operacion. Las lineas son una foto (jsonb): no se re-resuelven al leer.
CREATE TABLE actividad_edificio (
    id_actividad_edificio bigserial PRIMARY KEY,
    id_edificio           bigint       NOT NULL REFERENCES edificio (id_edificio),
    nombre_edificio       varchar(150) NOT NULL,
    operacion_id          varchar(36)  NOT NULL,
    usuario               varchar(255) NOT NULL,
    origen                varchar(20)  NOT NULL,
    motivo                varchar(500),
    ocurrido_en           timestamptz  NOT NULL,
    lineas                jsonb        NOT NULL,
    CONSTRAINT uq_actividad_edificio_operacion UNIQUE (operacion_id, id_edificio)
);
CREATE INDEX ix_actividad_edificio_edificio ON actividad_edificio (id_edificio, id_actividad_edificio DESC);

CREATE TABLE actividad_edificio_visto (
    usuario             varchar(255) PRIMARY KEY,
    id_ultima_actividad bigint NOT NULL
);
