-- AcademicEvent pasa a allocationSize = 50, igual que Occurrence: evento_academico se
-- llena en bulk durante el sync de SysAcad y una llamada a nextval por fila es cara.
--
-- En dev la secuencia ya se habia ajustado a mano (por eso el V1, que es la foto del
-- 04/09, todavia dice INCREMENT BY 1); en test quedo en 1. Esta migracion empareja los
-- dos y es idempotente.
ALTER SEQUENCE public.evento_academico_id_evento_academico_seq INCREMENT BY 50;
