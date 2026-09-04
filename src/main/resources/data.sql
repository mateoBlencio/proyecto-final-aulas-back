-- Seed de datos de referencia requeridos para el funcionamiento del sistema.
-- Spring Boot ejecuta este archivo automáticamente solo cuando
-- spring.sql.init.mode=always (activo únicamente en el perfil dev-local).
INSERT INTO tipo_aula (descripcion, creado_en, actualizado_en)
SELECT 'Normal', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM tipo_aula WHERE descripcion = 'Normal');

INSERT INTO tipo_recurso (nombre, tipo_valor, creado_en, actualizado_en)
SELECT v.nombre, v.tipo_valor, now(), now()
FROM (VALUES
    ('Cantidad de PC',     'COUNT'),
    ('Proyector',          'BOOLEAN'),
    ('Aire acondicionado', 'BOOLEAN')
) AS v(nombre, tipo_valor)
WHERE NOT EXISTS (SELECT 1 FROM tipo_recurso t WHERE t.nombre = v.nombre);

-- Admin inicial de dev-local. Password de desarrollo: "AdminSiga2026!" (BCrypt, no es un secreto real).
INSERT INTO usuario (correo, password_hash, habilitado, nombre, apellido)
SELECT 'admin@frc.utn.edu.ar', '$2y$10$entcSj2YEpWjWBz8UaCICuk98/ex9c9p4Gr4F.i/ZL6lRHA5OcjFy', true, 'Admin', 'Siga'
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE correo = 'admin@frc.utn.edu.ar');

INSERT INTO usuario_rol (id_usuario, id_rol, tipo_alcance, id_alcance)
SELECT u.id_usuario, r.id_rol, 'GLOBAL', NULL
FROM usuario u
JOIN rol r ON r.nombre = 'SUBSECRETARIA'
WHERE u.correo = 'admin@frc.utn.edu.ar'
  AND EXISTS (SELECT 1 FROM rol WHERE nombre = 'SUBSECRETARIA')
  AND NOT EXISTS (
      SELECT 1 FROM usuario_rol ur WHERE ur.id_usuario = u.id_usuario AND ur.id_rol = r.id_rol
  );