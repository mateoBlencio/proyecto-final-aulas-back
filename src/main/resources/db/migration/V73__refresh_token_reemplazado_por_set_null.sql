-- La purga de refresh tokens vencidos fallaba si otro token los referenciaba en reemplazado_por: al borrar, la referencia pasa a NULL.
ALTER TABLE refresh_token DROP CONSTRAINT fk_refresh_token_reemplazado_por;
ALTER TABLE refresh_token
    ADD CONSTRAINT fk_refresh_token_reemplazado_por FOREIGN KEY (reemplazado_por)
    REFERENCES refresh_token (id_refresh_token) ON DELETE SET NULL;
