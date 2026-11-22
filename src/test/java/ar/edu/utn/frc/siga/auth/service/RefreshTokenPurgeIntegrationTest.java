package ar.edu.utn.frc.siga.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.RefreshToken;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.RefreshTokenRepository;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class RefreshTokenPurgeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private User user;

    @AfterEach
    void cleanUp() {
        if (user == null || user.getId() == null) {
            return;
        }
        jdbc.update("delete from refresh_token where id_usuario = ?", user.getId());
        jdbc.update("delete from usuario where id_usuario = ?", user.getId());
    }

    @Test
    void purgeExpired_deletesOnlyTokensExpiredBeyondRetention() {
        user = userRepository.save(User.builder()
                .email("purge.test@frc.utn.edu.ar").passwordHash("irrelevant").enabled(true)
                .firstName("Purge").lastName("Test").build());
        Instant now = Instant.now();
        save("old", now.minus(Duration.ofDays(31)));
        save("recentlyExpired", now.minus(Duration.ofDays(5)));
        save("active", now.plus(Duration.ofDays(5)));

        int deleted = refreshTokenService.purgeExpired();

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(refreshTokenRepository.findByTokenHash("old")).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash("recentlyExpired")).isPresent();
        assertThat(refreshTokenRepository.findByTokenHash("active")).isPresent();
    }

    @Test
    void purgeExpired_whenAnotherTokenReferencesTheExpiredOne_doesNotFailAndClearsReplacedBy() {
        user = userRepository.save(User.builder()
                .email("purge.replaced@frc.utn.edu.ar").passwordHash("irrelevant").enabled(true)
                .firstName("Purge").lastName("Replaced").build());
        Instant now = Instant.now();
        RefreshToken expired = save("expiredReplaced", now.minus(Duration.ofDays(31)));
        RefreshToken successor = save("successor", now.plus(Duration.ofDays(5)));
        successor.setReplacedBy(expired);
        refreshTokenRepository.save(successor);

        int deleted = refreshTokenService.purgeExpired();

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(refreshTokenRepository.findByTokenHash("expiredReplaced")).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash("successor")).isPresent();
        assertThat(jdbc.queryForObject(
                "select reemplazado_por from refresh_token where token_hash = ?", Long.class, "successor"))
                .isNull();
    }

    private RefreshToken save(String hash, Instant expiresAt) {
        return refreshTokenRepository.save(
                RefreshToken.builder().user(user).tokenHash(hash).expiresAt(expiresAt).build());
    }
}
