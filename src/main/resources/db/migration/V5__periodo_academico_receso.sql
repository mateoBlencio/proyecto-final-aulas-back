-- El período ANUAL define el receso invernal (inicio/fin). Al setearlo, AcademicPeriodServiceImpl
-- sincroniza el fin del 1.º cuatrimestre y el inicio del 2.º. El orden y los límites se validan en
-- AcademicPeriodUpdateValidator, no en la base.
-- Numeración V5 según el plan maestro (V3 = RBAC, V4 = auditoría); Flyway no exige numeración contigua.
-- IF NOT EXISTS: dev y test pueden haber aplicado ddl-auto antes de esta migración.

ALTER TABLE periodo_academico ADD COLUMN IF NOT EXISTS fecha_inicio_receso date;
ALTER TABLE periodo_academico ADD COLUMN IF NOT EXISTS fecha_fin_receso date;
