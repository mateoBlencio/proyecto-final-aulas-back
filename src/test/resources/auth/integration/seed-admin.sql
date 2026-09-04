INSERT INTO usuario (correo, password_hash, habilitado, nombre, apellido)
SELECT 'admin.test@frc.utn.edu.ar', '$2y$10$o7WlgInnrDPs6RaXEE.P8.u5kv/Z.q6MxDmfAobeVTp50itKh4Yoi', true, 'Admin', 'Test'
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE correo = 'admin.test@frc.utn.edu.ar');

INSERT INTO usuario_rol (id_usuario, id_rol, tipo_alcance, id_alcance)
SELECT u.id_usuario, r.id_rol, 'GLOBAL', NULL
FROM usuario u
JOIN rol r ON r.nombre = 'SUBSECRETARIA'
WHERE u.correo = 'admin.test@frc.utn.edu.ar'
  AND NOT EXISTS (
      SELECT 1 FROM usuario_rol ur WHERE ur.id_usuario = u.id_usuario AND ur.id_rol = r.id_rol
  );

INSERT INTO usuario (correo, password_hash, habilitado, nombre, apellido)
SELECT 'auxiliar.test@frc.utn.edu.ar', '$2y$10$o7WlgInnrDPs6RaXEE.P8.u5kv/Z.q6MxDmfAobeVTp50itKh4Yoi', true, 'Auxiliar', 'Test'
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE correo = 'auxiliar.test@frc.utn.edu.ar');

INSERT INTO usuario_rol (id_usuario, id_rol, tipo_alcance, id_alcance)
SELECT u.id_usuario, r.id_rol, 'GLOBAL', NULL
FROM usuario u
JOIN rol r ON r.nombre = 'AUXILIAR_AULICO'
WHERE u.correo = 'auxiliar.test@frc.utn.edu.ar'
  AND NOT EXISTS (
      SELECT 1 FROM usuario_rol ur WHERE ur.id_usuario = u.id_usuario AND ur.id_rol = r.id_rol
  );

INSERT INTO usuario (correo, password_hash, habilitado, nombre, apellido)
SELECT 'disabled.test@frc.utn.edu.ar', '$2y$10$o7WlgInnrDPs6RaXEE.P8.u5kv/Z.q6MxDmfAobeVTp50itKh4Yoi', false, 'Disabled', 'Test'
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE correo = 'disabled.test@frc.utn.edu.ar');

INSERT INTO usuario_rol (id_usuario, id_rol, tipo_alcance, id_alcance)
SELECT u.id_usuario, r.id_rol, 'GLOBAL', NULL
FROM usuario u
JOIN rol r ON r.nombre = 'AUXILIAR_AULICO'
WHERE u.correo = 'disabled.test@frc.utn.edu.ar'
  AND NOT EXISTS (
      SELECT 1 FROM usuario_rol ur WHERE ur.id_usuario = u.id_usuario AND ur.id_rol = r.id_rol
  );
