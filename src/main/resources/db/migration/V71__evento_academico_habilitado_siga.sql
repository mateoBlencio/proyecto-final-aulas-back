ALTER TABLE evento_academico ADD COLUMN habilitado_siga boolean NOT NULL DEFAULT true;

-- Hasta ahora el sync guardaba en habilitado_sysacad la baja que decidía SIGA
-- (0 inscriptos o ausente). Esa decisión pasa a habilitado_siga.
UPDATE evento_academico SET habilitado_siga = false
 WHERE hash_raw IS NOT NULL AND habilitado_sysacad = false;

-- Los ausentes nunca retiraron sus ocurrencias futuras. Mismo criterio que
-- RecurringEventReconciler.retireFuture: se conservan las que tienen asignación o pedido.
-- Los de 0 inscriptos ya pasaron por retireFuture; repetirlo no borra nada nuevo.
-- "Hoy" en hora local, igual que LocalDate.now() de la aplicación.
DELETE FROM ocurrencia o
 USING evento_academico e
 WHERE o.id_evento_academico = e.id_evento_academico
   AND e.habilitado_siga = false
   AND o.fecha >= CAST((now() AT TIME ZONE 'America/Argentina/Buenos_Aires') AS date)
   AND NOT EXISTS (SELECT 1 FROM asignacion_aula a WHERE a.id_ocurrencia = o.id_ocurrencia)
   AND NOT EXISTS (SELECT 1 FROM solicitud_item_asignacion s WHERE s.id_ocurrencia = o.id_ocurrencia);
