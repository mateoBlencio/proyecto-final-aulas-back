package ar.edu.utn.frc.siga.auth.service;

import ar.edu.utn.frc.siga.auth.model.User;

public interface RefreshTokenService {

    /** Genera y persiste un nuevo refresh token para el usuario; devuelve el valor crudo. */
    IssuedRefreshToken issue(User user);

    /** Rota el refresh token dado y devuelve el par nuevo (email + roles vigentes del usuario). */
    RefreshResult refresh(String rawToken);

    /** Revoca esa sesión puntual; idempotente, no lanza error si el token no existe. */
    void revoke(String rawToken);

    void revokeAllByUserId(Integer userId);

    record IssuedRefreshToken(String rawToken, long expiresInSeconds) {
    }

    record RefreshResult(User user, IssuedRefreshToken refreshToken) {
    }
}
