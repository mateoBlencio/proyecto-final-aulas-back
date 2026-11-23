ALTER TABLE usuario_rol DROP CONSTRAINT chk_usuario_rol_alcance_por_rol;

UPDATE usuario_rol SET rol = 'ADMINISTRADOR' WHERE rol = 'SUBSECRETARIA';
UPDATE usuario_rol_aud SET rol = 'ADMINISTRADOR' WHERE rol = 'SUBSECRETARIA';

ALTER TABLE usuario_rol ADD CONSTRAINT chk_usuario_rol_alcance_por_rol
    CHECK ((rol <> 'ADMINISTRADOR' OR tipo_alcance = 'GLOBAL')
       AND (rol <> 'AUXILIAR_AULICO' OR tipo_alcance = 'BUILDING')) NOT VALID;
