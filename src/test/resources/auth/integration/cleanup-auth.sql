TRUNCATE refresh_token RESTART IDENTITY CASCADE;
DELETE FROM token_recuperacion_clave;
DELETE FROM notificacion WHERE plantilla IN ('PASSWORD_RESET', 'PASSWORD_CHANGED');
