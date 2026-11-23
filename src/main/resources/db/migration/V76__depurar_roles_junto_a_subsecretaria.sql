-- Un usuario SUBSECRETARIA ya tiene todos los permisos con alcance global: se borran sus otras asignaciones.
DELETE FROM usuario_rol
WHERE rol IN ('AUXILIAR_AULICO', 'CONSULTA')
  AND id_usuario IN (SELECT id_usuario FROM usuario_rol WHERE rol = 'SUBSECRETARIA');
