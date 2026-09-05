-- Seed de datos de referencia requeridos para el funcionamiento del sistema.
-- Spring Boot ejecuta este archivo automáticamente solo cuando
-- spring.sql.init.mode=always (activo únicamente en el perfil dev-local).

-- Tipo de aula por defecto. El codigo lo busca con upper(), por eso las comparaciones tambien.

-- Las bases viejas tienen 'Normal': se renombra para no dejar dos tipos y perder el que
-- las aulas ya referencian.
UPDATE tipo_aula
SET descripcion = 'Aula común', actualizado_en = now()
WHERE descripcion = 'Normal'
  AND NOT EXISTS (SELECT 1 FROM tipo_aula t WHERE upper(t.descripcion) = upper('Aula común'));

-- Si quedo borrado hay que revivirlo: el UNIQUE(descripcion) no deja insertar otro.
-- Una sola fila y solo si no hay ninguna activa; dos activas rompen la busqueda del sync.
UPDATE tipo_aula
SET eliminado_en = NULL, actualizado_en = now()
WHERE id_tipo_aula = (
        SELECT min(id_tipo_aula) FROM tipo_aula
        WHERE upper(descripcion) = upper('Aula común') AND eliminado_en IS NOT NULL)
  AND NOT EXISTS (
        SELECT 1 FROM tipo_aula t
        WHERE upper(t.descripcion) = upper('Aula común') AND t.eliminado_en IS NULL);

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