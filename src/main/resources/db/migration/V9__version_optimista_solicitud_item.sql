-- Lock optimista en solicitud_aula_item: dos resoluciones concurrentes sobre el mismo ítem
-- (dos auxiliares áulicos asignando/notificando/cancelando a la vez) podían pisarse sin que
-- ninguna fallara. @Version en la entidad hace que Hibernate incremente y valide esta columna
-- en cada UPDATE, así la segunda transacción en commitear choca con OptimisticLockException.
-- No se audita: Envers excluye @Version automáticamente, es un dato técnico, no de negocio.

ALTER TABLE solicitud_aula_item
    ADD COLUMN IF NOT EXISTS version bigint DEFAULT 0 NOT NULL;
