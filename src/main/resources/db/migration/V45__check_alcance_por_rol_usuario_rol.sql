-- SUBSECRETARIA is always GLOBAL and AUXILIAR_AULICO always BUILDING. NOT VALID: applies to new rows only;
-- existing rows are cleaned by hand and validated in a later migration (same path as V33 -> V42).
ALTER TABLE usuario_rol ADD CONSTRAINT chk_usuario_rol_alcance_por_rol
    CHECK ((rol <> 'SUBSECRETARIA' OR tipo_alcance = 'GLOBAL')
       AND (rol <> 'AUXILIAR_AULICO' OR tipo_alcance = 'BUILDING')) NOT VALID;
