-- Las fechas de decisión, derivación y notificación se escribían como hora de Buenos Aires sin zona;
-- se interpretan así al pasar a timestamptz.
ALTER TABLE solicitud_aula_item
    ALTER COLUMN fecha_decision TYPE timestamptz USING fecha_decision AT TIME ZONE 'America/Argentina/Buenos_Aires',
    ALTER COLUMN fecha_derivacion TYPE timestamptz USING fecha_derivacion AT TIME ZONE 'America/Argentina/Buenos_Aires',
    ALTER COLUMN fecha_notificacion TYPE timestamptz USING fecha_notificacion AT TIME ZONE 'America/Argentina/Buenos_Aires';

ALTER TABLE solicitud_aula_item_aud
    ALTER COLUMN fecha_decision TYPE timestamptz USING fecha_decision AT TIME ZONE 'America/Argentina/Buenos_Aires',
    ALTER COLUMN fecha_derivacion TYPE timestamptz USING fecha_derivacion AT TIME ZONE 'America/Argentina/Buenos_Aires',
    ALTER COLUMN fecha_notificacion TYPE timestamptz USING fecha_notificacion AT TIME ZONE 'America/Argentina/Buenos_Aires';
