package ar.edu.utn.frc.siga.auth.service;

import ar.edu.utn.frc.siga.auth.config.JwtProperties;
import ar.edu.utn.frc.siga.auth.exception.InvalidRefreshTokenException;
import ar.edu.utn.frc.siga.auth.model.RefreshToken;
import ar.edu.utn.frc.siga.auth.model.Role;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.RefreshTokenRepository;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.service.impl.RefreshTokenServiceImpl;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("integration")
class RefreshTokenServiceImplTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenServiceImpl refreshTokenService;
    private User user;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-fixed-secret-not-for-prod-0123456789abcdef");
        jwtProperties.setRefreshExpirationDays(30);
        jwtProperties.setRefreshGraceSeconds(10);

        refreshTokenService = new RefreshTokenServiceImpl(refreshTokenRepository, jwtProperties);

        user = userRepository.save(User.builder()
                .email("rotacion.test@frc.utn.edu.ar")
                .passwordHash("irrelevante")
                .enabled(true)
                .roles(Set.of(Role.AUXILIAR_AULICO))
                .build());
    }

    @Test
    void refresh_shouldRotateAndInvalidateThePreviousToken() {
        RefreshTokenService.IssuedRefreshToken issued = refreshTokenService.issue(user);

        RefreshTokenService.RefreshResult result = refreshTokenService.refresh(issued.rawToken());

        assertThat(result.refreshToken().rawToken()).isNotEqualTo(issued.rawToken());
    }

    @Test
    void refresh_shouldReturnFreshPair_whenRetriedWithinGraceWindow() {
        RefreshTokenService.IssuedRefreshToken issued = refreshTokenService.issue(user);
        RefreshTokenService.RefreshResult first = refreshTokenService.refresh(issued.rawToken());

        RefreshTokenService.RefreshResult retry = refreshTokenService.refresh(issued.rawToken());

        assertThat(retry.refreshToken().rawToken()).isNotEqualTo(first.refreshToken().rawToken());
        assertThat(retry.refreshToken().rawToken()).isNotEqualTo(issued.rawToken());
    }

    @Test
    void refresh_shouldCascadeAndThrow_whenReusedOutsideGraceWindow() {
        RefreshTokenService.IssuedRefreshToken issued = refreshTokenService.issue(user);
        refreshTokenService.refresh(issued.rawToken());

        // Simula que la rotación ocurrió hace mucho más que la ventana de gracia.
        String tokenHash = hashOf(issued.rawToken());
        RefreshToken revoked = refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        revoked.setRevokedAt(Instant.now().minusSeconds(3600));
        refreshTokenRepository.save(revoked);

        assertThatThrownBy(() -> refreshTokenService.refresh(issued.rawToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(refreshTokenRepository.findAll())
                .filteredOn(t -> t.getUser().getId().equals(user.getId()))
                .allSatisfy(t -> assertThat(t.getRevoked()).isTrue());
    }

    @Test
    void refresh_shouldReturn401_whenReusedTokenWasRevokedByLogout_evenWithinGraceWindow() {
        RefreshTokenService.IssuedRefreshToken issued = refreshTokenService.issue(user);
        refreshTokenService.revoke(issued.rawToken());

        assertThatThrownBy(() -> refreshTokenService.refresh(issued.rawToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void consumeByTokenHash_shouldAffectOnlyOneRow_whenCalledTwiceOnTheSameToken() {
        // Simula la condición de carrera: dos requests concurrentes intentan consumir el
        // mismo refresh token. La condición "revoked = false" en el UPDATE garantiza que
        // sólo el primero afecte una fila; el segundo debe ver 0 filas afectadas y caer en
        // la lógica de gracia en vez de emitir un segundo par válido.
        RefreshTokenService.IssuedRefreshToken issued = refreshTokenService.issue(user);
        RefreshToken parent = refreshTokenRepository.findByTokenHash(hashOf(issued.rawToken())).orElseThrow();

        RefreshToken child = refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash("hijo-de-prueba")
                .revoked(false)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build());

        int firstAttempt = refreshTokenRepository.consumeByTokenHash(parent.getTokenHash(), child, Instant.now());
        int secondAttempt = refreshTokenRepository.consumeByTokenHash(parent.getTokenHash(), child, Instant.now());

        assertThat(firstAttempt).isEqualTo(1);
        assertThat(secondAttempt).isEqualTo(0);
    }

    private String hashOf(String rawToken) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
