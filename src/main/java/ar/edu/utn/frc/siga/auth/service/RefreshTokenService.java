package ar.edu.utn.frc.siga.auth.service;

import ar.edu.utn.frc.siga.auth.model.User;

public interface RefreshTokenService {

    IssuedRefreshToken issue(User user);

    RefreshResult refresh(String rawToken);

    void revoke(String rawToken);

    void revokeAllByUserId(Long userId);

    record IssuedRefreshToken(String rawToken, long expiresInSeconds) {
    }

    record RefreshResult(User user, IssuedRefreshToken refreshToken) {
    }
}
