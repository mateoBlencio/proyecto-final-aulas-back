ALTER TABLE aula RENAME COLUMN hash_sysacad TO hash_raw;
ALTER TABLE comision RENAME COLUMN hash_sysacad TO hash_raw;
ALTER TABLE edificio RENAME COLUMN hash_sysacad TO hash_raw;
ALTER TABLE especialidad RENAME COLUMN hash_sysacad TO hash_raw;
ALTER TABLE evento_academico RENAME COLUMN hash_sysacad TO hash_raw;
ALTER TABLE materia RENAME COLUMN hash_sysacad TO hash_raw;
ALTER TABLE plan_estudio RENAME COLUMN hash_sysacad TO hash_raw;

ALTER TABLE aula RENAME COLUMN habilitada_sysacad TO raw_habilitada;
ALTER TABLE aula_aud RENAME COLUMN habilitada_sysacad TO raw_habilitada;
