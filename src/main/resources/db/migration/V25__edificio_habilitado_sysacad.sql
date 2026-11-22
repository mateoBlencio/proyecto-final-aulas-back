-- Vigencia en SysAcad del edificio. La ausencia en la vista solo escribe este flag; eliminado_en es de la persona.
ALTER TABLE edificio ADD COLUMN habilitado_sysacad boolean NOT NULL DEFAULT true;
