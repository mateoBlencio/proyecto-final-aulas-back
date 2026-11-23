package ar.edu.utn.frc.siga.auth.service.impl;

import ar.edu.utn.frc.siga.auth.config.JwtProperties;
import ar.edu.utn.frc.siga.auth.exception.InvalidCredentialsException;
import ar.edu.utn.frc.siga.auth.exception.InvalidRefreshTokenException;
import ar.edu.utn.frc.siga.auth.model.RefreshToken;
import ar.edu.utn.frc.siga.auth.model.RevocationReason;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.RefreshTokenRepository;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.security.OpaqueTokens;
import ar.edu.utn.frc.siga.auth.service.RefreshTokenService;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final Duration PURGE_RETENTION = Duration.ofDays(30);

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public IssuedRefreshToken issue(User user) {
        return doIssue(user);
    }

    @Override
    @Transactional
    public IssuedRefreshToken issueForLogin(User user, String authenticatedPasswordHash) {
        User locked = userRepository.findByIdForUpdate(user.getId()).orElseThrow(InvalidCredentialsException::new);
        if (!locked.getPasswordHash().equals(authenticatedPasswordHash) || !Boolean.TRUE.equals(locked.getEnabled())) {
            throw new InvalidCredentialsException();
        }
        return doIssue(locked);
    }

    private IssuedRefreshToken doIssue(User user) {
        String rawToken = OpaqueTokens.generate();
        long expirationSeconds = Duration.ofDays(jwtProperties.getRefreshExpirationDays()).toSeconds();
        Instant now = Instant.now();

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(OpaqueTokens.hash(rawToken))
                .revoked(false)
                .expiresAt(now.plusSeconds(expirationSeconds))
                .build();
        refreshTokenRepository.save(token);

        log.debug("Refresh token emitido: usuarioId={}", user.getId());
        return new IssuedRefreshToken(rawToken, expirationSeconds);
    }

    // noRollbackFor: the cascade revocation must persist even though the exception follows it
    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RefreshResult refresh(String rawToken) {
        String tokenHash = OpaqueTokens.hash(rawToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (Boolean.TRUE.equals(token.getRevoked())) {
            return handleReuse(token);
        }

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }

        User user = token.getUser();
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            revokeAllByUserId(user.getId());
            throw new InvalidRefreshTokenException();
        }

        return rotate(token, user);
    }

    private RefreshResult rotate(RefreshToken token, User user) {
        lockUser(user.getId());
        IssuedRefreshToken child = doIssue(user);
        RefreshToken childEntity = refreshTokenRepository.findByTokenHash(OpaqueTokens.hash(child.rawToken()))
                .orElseThrow(() -> new IllegalStateException("El refresh token recién emitido no se encontró"));

        int updated = refreshTokenRepository.consumeByTokenHash(token.getTokenHash(), childEntity, Instant.now());

        if (updated == 1) {
            return new RefreshResult(user, child);
        }

        refreshTokenRepository.delete(childEntity);
        RefreshToken reloaded = refreshTokenRepository.findByTokenHash(token.getTokenHash())
                .orElseThrow(InvalidRefreshTokenException::new);
        return handleReuse(reloaded);
    }

    private RefreshResult handleReuse(RefreshToken token) {
        boolean withinGrace = token.getRevokedAt() != null
                && Duration.between(token.getRevokedAt(), Instant.now())
                        .compareTo(Duration.ofSeconds(jwtProperties.getRefreshGraceSeconds())) <= 0;

        // Trade-off: within refresh-grace-seconds a reused rotated token still yields a new token, so a thief
        // replaying a stolen token in that window is not detected; accepted to tolerate network retries.
        if (token.getRevocationReason() == RevocationReason.ROTATION && withinGrace) {
            lockUser(token.getUser().getId());
            boolean stillRotated = refreshTokenRepository.findRevocationReason(token.getTokenHash())
                    .filter(reason -> reason == RevocationReason.ROTATION)
                    .isPresent();
            if (!stillRotated) {
                throw new InvalidRefreshTokenException();
            }
            log.info("Refresh token reutilizado dentro de la ventana de gracia, tratado como reintento de red");
            return new RefreshResult(token.getUser(), doIssue(token.getUser()));
        }

        if (token.getRevocationReason() == RevocationReason.ROTATION) {
            log.warn("Reuso de refresh token fuera de la ventana de gracia, revocación en cascada: usuarioId={}",
                    token.getUser().getId());
            revokeAllByUserId(token.getUser().getId());
        }

        throw new InvalidRefreshTokenException();
    }

    private void lockUser(Long userId) {
        userRepository.findByIdForUpdate(userId);
    }

    @Override
    @Transactional
    public void revoke(String rawToken) {
        String tokenHash = OpaqueTokens.hash(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token ->
                log.info("Refresh token revocado por logout: usuarioId={}", token.getUser().getId()));
        refreshTokenRepository.revokeByTokenHash(tokenHash, Instant.now());
    }

    @Override
    @Transactional
    public void revokeAllByUserId(Long userId) {
        lockUser(userId);
        refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
    }

    @Override
    @Transactional
    public int purgeExpired() {
        return refreshTokenRepository.deleteExpiredBefore(Instant.now().minus(PURGE_RETENTION));
    }
}
