-- asignacion_aula, evento_academico and ocurrencia take ids from <table>_<id>_seq through Hibernate, but the
-- column DEFAULT pointed to an unused <seq>1 sequence: a plain SQL insert got id 1 and hit the primary key.
ALTER TABLE asignacion_aula ALTER COLUMN id_asignacion SET DEFAULT nextval('asignacion_aula_id_asignacion_seq');
ALTER SEQUENCE asignacion_aula_id_asignacion_seq OWNED BY asignacion_aula.id_asignacion;
DROP SEQUENCE asignacion_aula_id_asignacion_seq1;

ALTER TABLE evento_academico ALTER COLUMN id_evento_academico SET DEFAULT nextval('evento_academico_id_evento_academico_seq');
ALTER SEQUENCE evento_academico_id_evento_academico_seq OWNED BY evento_academico.id_evento_academico;
DROP SEQUENCE evento_academico_id_evento_academico_seq1;

ALTER TABLE ocurrencia ALTER COLUMN id_ocurrencia SET DEFAULT nextval('ocurrencia_id_ocurrencia_seq');
ALTER SEQUENCE ocurrencia_id_ocurrencia_seq OWNED BY ocurrencia.id_ocurrencia;
DROP SEQUENCE ocurrencia_id_ocurrencia_seq1;

-- Orphan sequences from the baseline: their tables use IDENTITY or the <seq>1 default and no entity names them.
DROP SEQUENCE IF EXISTS aula_id_aula_seq;
DROP SEQUENCE IF EXISTS comision_id_comision_seq;
DROP SEQUENCE IF EXISTS edificio_id_edificio_seq;
DROP SEQUENCE IF EXISTS especialidad_id_especialidad_seq;
DROP SEQUENCE IF EXISTS materia_comision_id_materia_comision_seq;
DROP SEQUENCE IF EXISTS materia_id_materia_seq;
DROP SEQUENCE IF EXISTS periodo_academico_id_periodo_seq;
DROP SEQUENCE IF EXISTS plan_estudio_id_plan_seq;
DROP SEQUENCE IF EXISTS refresh_token_id_refresh_token_seq;
DROP SEQUENCE IF EXISTS revinfo_rev_seq;
DROP SEQUENCE IF EXISTS solicitud_aula_id_solicitud_seq;
DROP SEQUENCE IF EXISTS solicitud_aula_item_id_item_seq;
DROP SEQUENCE IF EXISTS solicitud_aula_preferencia_id_preferencia_seq;
DROP SEQUENCE IF EXISTS sysacad_sync_state_id_estado_sync_seq;
DROP SEQUENCE IF EXISTS tipo_aula_id_tipo_aula_seq;
DROP SEQUENCE IF EXISTS usuario_id_usuario_seq;
