-- Distingue revisiones hechas por una persona de las hechas por el sistema (schedulers, listeners).
-- El UPDATE clasifica las revisiones historicas sin usuario: las que crearon una solicitud de aula
-- vienen del formulario publico (sin sesion) y quedan HUMAN; el resto queda SYSTEM.
-- El DEFAULT existe solo para rellenar las filas previas; se quita al final para que un INSERT
-- que omita la columna falle en vez de quedar como HUMAN (Hibernate siempre setea el valor).
ALTER TABLE revinfo ADD COLUMN tipo_actor varchar(10) NOT NULL DEFAULT 'HUMAN';
ALTER TABLE revinfo ADD CONSTRAINT revinfo_tipo_actor_check CHECK (tipo_actor IN ('HUMAN', 'SYSTEM'));

UPDATE revinfo r SET tipo_actor = 'SYSTEM'
 WHERE r.usuario IS NULL
   AND NOT EXISTS (SELECT 1 FROM solicitud_aula_aud s WHERE s.rev = r.rev AND s.revtype = 0);

ALTER TABLE revinfo ALTER COLUMN tipo_actor DROP DEFAULT;
