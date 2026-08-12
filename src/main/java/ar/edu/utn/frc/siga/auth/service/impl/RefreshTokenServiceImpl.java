package ar.edu.utn.frc.siga.auth.service.impl;

import ar.edu.utn.frc.siga.auth.config.JwtProperties;
import ar.edu.utn.frc.siga.auth.exception.InvalidRefreshTokenException;
import ar.edu.utn.frc.siga.auth.model.RefreshToken;
import ar.edu.utn.frc.siga.auth.model.RevocationReason;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.RefreshTokenRepository;
import ar.edu.utn.frc.siga.auth.service.RefreshTokenService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final int RAW_TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public IssuedRefreshToken issue(User user) {
        String rawToken = generateRawToken();
        long expirationSeconds = Duration.ofDays(jwtProperties.getRefreshExpirationDays()).toSeconds();
        Instant now = Instant.now();

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .revoked(false)
                .expiresAt(now.plusSeconds(expirationSeconds))
                .createdAt(now)
                .build();
        refreshTokenRepository.save(token);

        log.debug("Refresh token emitido: usuarioId={}", user.getId());
        return new IssuedRefreshToken(rawToken, expirationSeconds);
    }

    @Override
    @Transactional
    public RefreshResult refresh(String rawToken) {
        String tokenHash = hash(rawToken);
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
        IssuedRefreshToken child = issue(user);
        RefreshToken childEntity = refreshTokenRepository.findByTokenHash(hash(child.rawToken()))
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

        if (token.getRevocationReason() == RevocationReason.ROTATION && withinGrace) {
            log.info("Refresh token reutilizado dentro de la ventana de gracia, tratado como reintento de red");
            return new RefreshResult(token.getUser(), issue(token.getUser()));
        }

        if (token.getRevocationReason() == RevocationReason.ROTATION) {
            log.warn("Reuso de refresh token fuera de la ventana de gracia, revocación en cascada: usuarioId={}",
                    token.getUser().getId());
            revokeAllByUserId(token.getUser().getId());
        }

        throw new InvalidRefreshTokenException();
    }

    @Override
    @Transactional
    public void revoke(String rawToken) {
        String tokenHash = hash(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token ->
                log.info("Refresh token revocado por logout: usuarioId={}", token.getUser().getId()));
        refreshTokenRepository.revokeByTokenHash(tokenHash, Instant.now());
    }

    @Override
    @Transactional
    public void revokeAllByUserId(Integer userId) {
        refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[RAW_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en la JVM", e);
        }
    }
}
