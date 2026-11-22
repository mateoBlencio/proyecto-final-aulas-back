-- Tipo CONFERENCIA: el CHECK de V1 enumera los cuatro valores viejos
ALTER TABLE evento_unico DROP CONSTRAINT chk_evento_unico_tipo_actividad;
ALTER TABLE evento_unico ADD CONSTRAINT chk_evento_unico_tipo_actividad
    CHECK (tipo_actividad IN ('PARCIAL', 'TRABAJO_PRACTICO', 'EXAMEN_FINAL', 'CONFERENCIA', 'OTRO'));

-- Descripción: V1 la dejó en varchar(255) y el formulario admite 1000
ALTER TABLE evento_unico     ALTER COLUMN descripcion TYPE varchar(1000);
ALTER TABLE evento_unico_aud ALTER COLUMN descripcion TYPE varchar(1000);

-- Requisitos de aula del evento. Los eventos existentes quedan como "no pedía nada".
ALTER TABLE evento_unico ADD COLUMN requiere_proyector       boolean NOT NULL DEFAULT false;
ALTER TABLE evento_unico ADD COLUMN requiere_computadoras    boolean NOT NULL DEFAULT false;
ALTER TABLE evento_unico ADD COLUMN cantidad_computadoras    integer;
ALTER TABLE evento_unico ADD COLUMN software_requerido       varchar(255);
ALTER TABLE evento_unico ADD COLUMN requiere_usuarios_examen boolean;

-- Auditoría: nullable y sin CHECK, las revisiones viejas no tienen el dato
ALTER TABLE evento_unico_aud ADD COLUMN requiere_proyector       boolean;
ALTER TABLE evento_unico_aud ADD COLUMN requiere_computadoras    boolean;
ALTER TABLE evento_unico_aud ADD COLUMN cantidad_computadoras    integer;
ALTER TABLE evento_unico_aud ADD COLUMN software_requerido       varchar(255);
ALTER TABLE evento_unico_aud ADD COLUMN requiere_usuarios_examen boolean;

ALTER TABLE evento_unico ADD CONSTRAINT chk_evento_unico_computadoras
    CHECK ((requiere_computadoras AND cantidad_computadoras IS NOT NULL)
        OR (NOT requiere_computadoras AND cantidad_computadoras IS NULL AND software_requerido IS NULL));
