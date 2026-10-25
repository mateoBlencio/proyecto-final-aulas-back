-- Envers audit tables for the space and academic catalogs (Building, Classroom, ClassroomType,
-- ClassroomResource, ClassroomPermission, ResourceType, Holiday, Commission, Subject, Specialty, StudyPlan,
-- AcademicPeriod). Same shape as regeneracion_tablero_aud (V22): nullable data columns, no CHECK.
-- sincronizado_en, hash_sysacad, version, creado_en and actualizado_en are not audited: they are sync
-- bookkeeping, the optimistic lock and timestamps. SubjectCommission (composite key) is not audited.
-- The initial state of the existing rows is copied in V50.

CREATE TABLE edificio_aud (
    id_edificio bigint NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    codigo_edificio integer,
    nombre varchar(100),
    habilitado_sysacad boolean,
    eliminado_en timestamp,
    PRIMARY KEY (id_edificio, rev)
);
CREATE INDEX idx_edificio_aud_rev ON edificio_aud (rev);

CREATE TABLE aula_aud (
    id_aula bigint NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    numero integer,
    capacidad integer,
    habilitada_sysacad boolean,
    vigente_sysacad boolean,
    id_edificio bigint,
    id_tipo_aula bigint,
    modo_permiso varchar(20),
    observaciones varchar(500),
    eliminado_en timestamp,
    PRIMARY KEY (id_aula, rev)
);
CREATE INDEX idx_aula_aud_rev ON aula_aud (rev);

CREATE TABLE tipo_aula_aud (
    id_tipo_aula bigint NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    descripcion varchar(50),
    eliminado_en timestamp,
    PRIMARY KEY (id_tipo_aula, rev)
);
CREATE INDEX idx_tipo_aula_aud_rev ON tipo_aula_aud (rev);

CREATE TABLE aula_recurso_aud (
    id_aula_recurso bigint NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    id_aula bigint,
    id_tipo_recurso bigint,
    cantidad integer,
    eliminado_en timestamp,
    PRIMARY KEY (id_aula_recurso, rev)
);
CREATE INDEX idx_aula_recurso_aud_rev ON aula_recurso_aud (rev);

CREATE TABLE aula_permiso_aud (
    id_aula_permiso bigint NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    id_aula bigint,
    tipo_objetivo varchar(20),
    id_objetivo bigint,
    eliminado_en timestamp,
    PRIMARY KEY (id_aula_permiso, rev)
);
CREATE INDEX idx_aula_permiso_aud_rev ON aula_permiso_aud (rev);

CREATE TABLE tipo_recurso_aud (
    id_tipo_recurso bigint NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    nombre varchar(100),
    tipo_valor varchar(20),
    eliminado_en timestamp,
    PRIMARY KEY (id_tipo_recurso, rev)
);
CREATE INDEX idx_tipo_recurso_aud_rev ON tipo_recurso_aud (rev);

CREATE TABLE feriado_aud (
    fecha date NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    descripcion varchar(255),
    PRIMARY KEY (fecha, rev)
);
CREATE INDEX idx_feriado_aud_rev ON feriado_aud (rev);

CREATE TABLE comision_aud (
    id_comision bigint NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    codigo_curso varchar(255),
    id_periodo_academico bigint,
    habilitado_sysacad boolean,
    eliminado_en timestamp,
    PRIMARY KEY (id_comision, rev)
);
CREATE INDEX idx_comision_aud_rev ON comision_aud (rev);

CREATE TABLE materia_aud (
    id_materia bigint NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    codigo_materia integer,
    nombre varchar(255),
    id_plan bigint,
    dictado varchar(255),
    eliminado_en timestamp,
    PRIMARY KEY (id_materia, rev)
);
CREATE INDEX idx_materia_aud_rev ON materia_aud (rev);

CREATE TABLE especialidad_aud (
    id_especialidad bigint NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    codigo_especialidad integer,
    nombre varchar(255),
    abreviatura varchar(255),
    eliminado_en timestamp,
    PRIMARY KEY (id_especialidad, rev)
);
CREATE INDEX idx_especialidad_aud_rev ON especialidad_aud (rev);

CREATE TABLE plan_estudio_aud (
    id_plan bigint NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    codigo_plan integer,
    id_especialidad bigint,
    eliminado_en timestamp,
    PRIMARY KEY (id_plan, rev)
);
CREATE INDEX idx_plan_estudio_aud_rev ON plan_estudio_aud (rev);

CREATE TABLE periodo_academico_aud (
    id_periodo bigint NOT NULL,
    rev integer NOT NULL REFERENCES revinfo (rev),
    revtype smallint,
    anio integer,
    cuatrimestre integer,
    fecha_inicio date,
    fecha_fin date,
    fecha_inicio_receso date,
    fecha_fin_receso date,
    eliminado_en timestamp,
    PRIMARY KEY (id_periodo, rev)
);
CREATE INDEX idx_periodo_academico_aud_rev ON periodo_academico_aud (rev);
