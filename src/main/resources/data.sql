-- Seed de datos de referencia requeridos para el funcionamiento del sistema.
-- Spring Boot ejecuta este archivo automáticamente solo cuando
-- spring.sql.init.mode=always (activo únicamente en el perfil dev-local).
INSERT INTO tipo_aula (descripcion, eliminado, creado_en, actualizado_en)
SELECT 'Normal', false, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM tipo_aula WHERE descripcion = 'Normal');

-- Admin inicial de dev-local. Password de desarrollo: "AdminSiga2026!" (BCrypt, no es un secreto real).
INSERT INTO usuario (correo, password_hash, habilitado, eliminado, creado_en, actualizado_en)
SELECT 'admin@frc.utn.edu.ar', '$2y$10$entcSj2YEpWjWBz8UaCICuk98/ex9c9p4Gr4F.i/ZL6lRHA5OcjFy', true, false, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE correo = 'admin@frc.utn.edu.ar');

INSERT INTO usuario_rol (id_usuario, rol)
SELECT u.id_usuario, 'SUBSECRETARIA'
FROM usuario u
WHERE u.correo = 'admin@frc.utn.edu.ar'
  AND NOT EXISTS (
      SELECT 1 FROM usuario_rol ur WHERE ur.id_usuario = u.id_usuario AND ur.rol = 'SUBSECRETARIA'
  );