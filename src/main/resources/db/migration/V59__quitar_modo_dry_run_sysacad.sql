UPDATE configuracion SET valor = 'OFF' WHERE clave = 'sysacad.write.mode' AND valor = 'DRY_RUN';

UPDATE sysacad_horario_registro SET tipo = 'SANDBOX_SEND' WHERE tipo = 'DRY_RUN_SEND';

DELETE FROM sysacad_horario_registro WHERE tipo = 'DIRECT_CHANGE_DRY_RUN';

DELETE FROM configuracion WHERE clave IN ('sysacad.sync.dayOfWeek', 'sysacad.sync.time');
