-- habilitada_sysacad mezclaba dos cosas: el flag S/N de Sysacad y "el aula dejó de listarse". Ahora
-- habilitada_sysacad es solo el flag S/N (solo "N" saca el aula de las candidatas) y vigente_sysacad indica
-- que la última sincronización la listó (ausente no saca el aula).
ALTER TABLE aula ADD COLUMN vigente_sysacad boolean NOT NULL DEFAULT true;

-- Las filas sincronizadas con habilitada_sysacad = false eran "N" o ausentes y no se pueden distinguir. Se
-- las deja habilitadas hasta la próxima sincronización, que reescribe el flag con el valor real de Sysacad
-- (el hash no cambia, pero la comparación ahora incluye el flag) y marca vigente_sysacad = false a las ausentes.
UPDATE aula SET habilitada_sysacad = true WHERE hash_sysacad IS NOT NULL;
