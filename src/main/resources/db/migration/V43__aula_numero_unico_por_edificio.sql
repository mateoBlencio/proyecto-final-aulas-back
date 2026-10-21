-- Room numbers repeat across buildings; Java now validates per building, this index backs it against concurrent inserts.
-- Fails if an active building already holds the same number twice. Check first on dev/prod (must return 0 rows):
--   SELECT id_edificio, numero, count(*) FROM aula WHERE eliminado_en IS NULL GROUP BY 1, 2 HAVING count(*) > 1;
CREATE UNIQUE INDEX uq_aula_edificio_numero ON aula (id_edificio, numero) WHERE eliminado_en IS NULL;
