-- The daily dashboard tables came from a V23 that was reverted in code but stayed applied in dev.
-- IF EXISTS makes this a no-op on fresh databases and tests.
DROP TABLE IF EXISTS regeneracion_tablero_dia_aud, regeneracion_tablero_dia, tablero_dia_edificio, tablero_dia;
