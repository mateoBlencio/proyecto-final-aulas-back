-- chk_usuario_rol_alcance (V33) quedó NOT VALID: solo regía para filas nuevas.
-- Se valida contra las filas existentes; si alguna es BUILDING sin edificio, la migración falla.
ALTER TABLE usuario_rol VALIDATE CONSTRAINT chk_usuario_rol_alcance;
