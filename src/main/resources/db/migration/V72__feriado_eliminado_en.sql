-- Baja lógica de feriados: el sync marca los futuros que SysAcad dejó de listar en vez de borrarlos.
ALTER TABLE feriado ADD COLUMN eliminado_en timestamp(6);
ALTER TABLE feriado_aud ADD COLUMN eliminado_en timestamp(6);
