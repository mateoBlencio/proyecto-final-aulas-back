package ar.edu.utn.frc.siga.auth.service;

import ar.edu.utn.frc.siga.auth.model.User;

public interface RefreshTokenService {

    IssuedRefreshToken issue(User user);

    RefreshResult refresh(String rawToken);

    void revoke(String rawToken);

    void revokeAllByUserId(Long userId);

    /** Deletes tokens that expired more than the retention period ago; returns how many. */
    int purgeExpired();

    record IssuedRefreshToken(String rawToken, long expiresInSeconds) {
    }

    record RefreshResult(User user, IssuedRefreshToken refreshToken) {
    }
}
