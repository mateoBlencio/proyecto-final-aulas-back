-- Java normaliza el correo (strip + minúsculas) al dar de alta y al buscar; esto alinea los datos previos
-- y evita dos usuarios que difieran solo en mayúsculas. usuario_aud no se toca: es historial.
-- Falla si ya hay correos que difieren solo en mayúsculas o espacios. Verificar antes (debe dar 0 filas):
--   SELECT lower(btrim(correo)), count(*) FROM usuario GROUP BY 1 HAVING count(*) > 1;
UPDATE usuario SET correo = lower(btrim(correo)) WHERE correo <> lower(btrim(correo));

CREATE UNIQUE INDEX uq_usuario_correo_lower ON usuario (lower(correo));

ALTER TABLE usuario DROP CONSTRAINT usuario_correo_key;
