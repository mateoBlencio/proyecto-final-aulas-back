-- Seed de datos de referencia requeridos para el funcionamiento del sistema.
-- Spring Boot ejecuta este archivo automáticamente solo cuando
-- spring.sql.init.mode=always (activo únicamente en el perfil dev-local).

-- Tipo de aula por defecto. Las tres sentencias son idempotentes y van en este orden.
-- El codigo lo busca sin distinguir mayusculas, por eso las comparaciones usan upper().

-- 1) Bases sembradas antes del cambio tienen el 'Normal' viejo, y es el que ya usan sus
-- aulas: se renombra en vez de crear uno nuevo al lado. Match exacto porque el
-- UNIQUE(descripcion) garantiza que a lo sumo hay una fila asi.
UPDATE tipo_aula
SET descripcion = 'Aula común', actualizado_en = now()
WHERE descripcion = 'Normal'
  AND NOT EXISTS (SELECT 1 FROM tipo_aula t WHERE upper(t.descripcion) = upper('Aula común'));

-- 2) Si quedo borrado logicamente hay que revivirlo: el UNIQUE(descripcion) no deja
-- insertar otro. Revive una sola fila y solo si no hay ninguna activa, porque dos activas
-- que matcheen romperian la busqueda del sync con IncorrectResultSizeDataAccessException.
UPDATE tipo_aula
SET eliminado_en = NULL, actualizado_en = now()
WHERE id_tipo_aula = (
        SELECT min(id_tipo_aula) FROM tipo_aula
        WHERE upper(descripcion) = upper('Aula común') AND eliminado_en IS NOT NULL)
  AND NOT EXISTS (
        SELECT 1 FROM tipo_aula t
        WHERE upper(t.descripcion) = upper('Aula común') AND t.eliminado_en IS NULL);

-- 3) Base nueva: no habia nada que renombrar ni revivir.
INSERT INTO tipo_aula (descripcion, creado_en, actualizado_en)
SELECT 'Aula común', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM tipo_aula WHERE upper(descripcion) = upper('Aula común'));

INSERT INTO tipo_recurso (nombre, tipo_valor, creado_en, actualizado_en)
SELECT v.nombre, v.tipo_valor, now(), now()
FROM (VALUES
    ('Cantidad de PC',     'COUNT'),
    ('Proyector',          'BOOLEAN'),
    ('Aire acondicionado', 'BOOLEAN')
) AS v(nombre, tipo_valor)
WHERE NOT EXISTS (SELECT 1 FROM tipo_recurso t WHERE t.nombre = v.nombre);

-- Admin inicial de dev-local. Password de desarrollo: "AdminSiga2026!" (BCrypt, no es un secreto real).
INSERT INTO usuario (correo, password_hash, habilitado)
SELECT 'admin@frc.utn.edu.ar', '$2y$10$entcSj2YEpWjWBz8UaCICuk98/ex9c9p4Gr4F.i/ZL6lRHA5OcjFy', true
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE correo = 'admin@frc.utn.edu.ar');

INSERT INTO usuario_rol (id_usuario, rol)
SELECT u.id_usuario, 'SUBSECRETARIA'
FROM usuario u
WHERE u.correo = 'admin@frc.utn.edu.ar'
  AND NOT EXISTS (
      SELECT 1 FROM usuario_rol ur WHERE ur.id_usuario = u.id_usuario AND ur.rol = 'SUBSECRETARIA'
  );