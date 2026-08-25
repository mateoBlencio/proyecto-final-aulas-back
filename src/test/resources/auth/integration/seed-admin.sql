-- Usuarios de prueba fijos, reutilizados por @Sql en los tests de integración que
-- necesitan autenticarse. Password de los tres: "TestPassword123!" (BCrypt, no es un secreto real).
INSERT INTO usuario (correo, password_hash, habilitado)
SELECT 'admin.test@frc.utn.edu.ar', '$2y$10$o7WlgInnrDPs6RaXEE.P8.u5kv/Z.q6MxDmfAobeVTp50itKh4Yoi', true
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE correo = 'admin.test@frc.utn.edu.ar');

INSERT INTO usuario_rol (id_usuario, rol)
SELECT u.id_usuario, 'SUBSECRETARIA'
FROM usuario u
WHERE u.correo = 'admin.test@frc.utn.edu.ar'
  AND NOT EXISTS (SELECT 1 FROM usuario_rol ur WHERE ur.id_usuario = u.id_usuario AND ur.rol = 'SUBSECRETARIA');

INSERT INTO usuario (correo, password_hash, habilitado)
SELECT 'auxiliar.test@frc.utn.edu.ar', '$2y$10$o7WlgInnrDPs6RaXEE.P8.u5kv/Z.q6MxDmfAobeVTp50itKh4Yoi', true
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE correo = 'auxiliar.test@frc.utn.edu.ar');

INSERT INTO usuario_rol (id_usuario, rol)
SELECT u.id_usuario, 'AUXILIAR_AULICO'
FROM usuario u
WHERE u.correo = 'auxiliar.test@frc.utn.edu.ar'
  AND NOT EXISTS (SELECT 1 FROM usuario_rol ur WHERE ur.id_usuario = u.id_usuario AND ur.rol = 'AUXILIAR_AULICO');

INSERT INTO usuario (correo, password_hash, habilitado)
SELECT 'disabled.test@frc.utn.edu.ar', '$2y$10$o7WlgInnrDPs6RaXEE.P8.u5kv/Z.q6MxDmfAobeVTp50itKh4Yoi', false
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE correo = 'disabled.test@frc.utn.edu.ar');

INSERT INTO usuario_rol (id_usuario, rol)
SELECT u.id_usuario, 'AUXILIAR_AULICO'
FROM usuario u
WHERE u.correo = 'disabled.test@frc.utn.edu.ar'
  AND NOT EXISTS (SELECT 1 FROM usuario_rol ur WHERE ur.id_usuario = u.id_usuario AND ur.rol = 'AUXILIAR_AULICO');
