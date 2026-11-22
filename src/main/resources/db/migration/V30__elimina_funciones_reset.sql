-- Unused SECURITY DEFINER helpers from the baseline. reset_asignaciones truncates asignacion_aula and its
-- audit table without leaving a revision; reset_eventos references tables that no longer exist.
DROP FUNCTION IF EXISTS public.reset_asignaciones(boolean);
DROP FUNCTION IF EXISTS public.reset_eventos(boolean);
