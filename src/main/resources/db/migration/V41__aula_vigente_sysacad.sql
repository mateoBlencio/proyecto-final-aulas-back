-- habilitada_sysacad mixed two things: Sysacad's S/N flag and "the classroom stopped being listed". Now
-- habilitada_sysacad is only the S/N flag (only "N" removes the classroom from the candidates) and
-- vigente_sysacad says the last sync listed it (absent does not remove the classroom).
ALTER TABLE aula ADD COLUMN vigente_sysacad boolean NOT NULL DEFAULT true;

-- Synced rows with habilitada_sysacad = false were either "N" or absent and cannot be told apart. They are
-- left enabled until the next sync, which rewrites the flag with Sysacad's real value (the hash does not
-- change, but the comparison now includes the flag) and sets vigente_sysacad = false on the absent ones.
UPDATE aula SET habilitada_sysacad = true WHERE hash_sysacad IS NOT NULL;
