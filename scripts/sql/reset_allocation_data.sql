-- =============================================================================
-- reset_allocation_data.sql
-- =============================================================================
--
-- Qué es:
--   Borra todo el contenido de las tablas de allocation (asignacion_aula,
--   evento_academico, evento_recurrente, evento_unico_academico, ocurrencia)
--   y reinicia la numeración de sus PKs (IDENTITY) a 1. Borra también el
--   contenido de sus tablas de auditoría Envers (*_aud) y de revinfo, sin
--   reiniciar numeración en esas (no se pidió).
--
-- Por qué:
--   Limpieza manual de datos de prueba/carga en un entorno compartido sin
--   TRUNCATE/DDL disponible para el usuario de aplicación.
--
-- Requiere:
--   DELETE en las 10 tablas (ya otorgado). Los RESTART IDENTITY al final
--   requieren privilegio ALTER sobre esas 5 tablas, que normalmente NO está
--   en el grant de la app (solo INSERT/SELECT/UPDATE/DELETE). Si fallan con
--   "permission denied", pedirle al DBA que corra solo esos 5 ALTER TABLE,
--   o correr el script completo con un rol que sea owner de las tablas.
--
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1) Datos "vivos": borrar hijos antes que padres (FKs)
-- -----------------------------------------------------------------------------
DELETE FROM public.asignacion_aula;
DELETE FROM public.ocurrencia;
DELETE FROM public.evento_recurrente;
DELETE FROM public.evento_unico_academico;
DELETE FROM public.evento_academico;

-- -----------------------------------------------------------------------------
-- 2) Auditoría Envers: mismo orden de hijos a padres, + revinfo al final
--    (evento_recurrente_aud / evento_unico_academico_aud no tienen secuencia
--    propia, no se reinicia numeración en ninguna _aud ni en revinfo)
-- -----------------------------------------------------------------------------
DELETE FROM public.asignacion_aula_aud;
DELETE FROM public.ocurrencia_aud;
DELETE FROM public.evento_recurrente_aud;
DELETE FROM public.evento_unico_academico_aud;
DELETE FROM public.evento_academico_aud;
DELETE FROM public.revinfo;

-- -----------------------------------------------------------------------------
-- 3) Reiniciar numeración de PKs IDENTITY en las tablas "vivas"
--    (requiere privilegio ALTER, ver nota de arriba)
-- -----------------------------------------------------------------------------
ALTER TABLE public.asignacion_aula ALTER COLUMN id_asignacion RESTART WITH 1;
ALTER TABLE public.ocurrencia ALTER COLUMN id_ocurrencia RESTART WITH 1;
ALTER TABLE public.evento_academico ALTER COLUMN id_evento_academico RESTART WITH 1;

COMMIT;