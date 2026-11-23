package ar.edu.utn.frc.siga.auth.service.impl;

import ar.edu.utn.frc.siga.auth.config.JwtProperties;
import ar.edu.utn.frc.siga.auth.exception.InvalidRefreshTokenException;
import ar.edu.utn.frc.siga.auth.model.RefreshToken;
import ar.edu.utn.frc.siga.auth.model.RevocationReason;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.RefreshTokenRepository;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.security.OpaqueTokens;
import ar.edu.utn.frc.siga.auth.service.RefreshTokenService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenServiceImpl: rama de gracia")
class RefreshTokenServiceImplGraceTest {

    private static final String RAW_TOKEN = "token-rotado";
    private static final Long USER_ID = 7L;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserRepository userRepository;

    private RefreshTokenServiceImpl service;
    private RefreshToken rotated;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-fixed-secret-not-for-prod-0123456789abcdef");
        jwtProperties.setRefreshExpirationDays(30);
        jwtProperties.setRefreshGraceSeconds(10);
        service = new RefreshTokenServiceImpl(refreshTokenRepository, jwtProperties, userRepository);

        User user = User.builder().id(USER_ID).email("gracia@frc.utn.edu.ar").enabled(true).build();
        rotated = RefreshToken.builder()
                .user(user)
                .tokenHash(OpaqueTokens.hash(RAW_TOKEN))
                .revoked(true)
                .revokedAt(Instant.now())
                .revocationReason(RevocationReason.ROTATION)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(refreshTokenRepository.findByTokenHash(rotated.getTokenHash())).thenReturn(Optional.of(rotated));
    }

    @Test
    @DisplayName("si una revocación total commiteó mientras esperaba el lock (CASCADE), rechaza y no emite")
    void graceRetry_rejectsWhenReasonBecameCascade() {
        when(refreshTokenRepository.findRevocationReason(rotated.getTokenHash()))
                .thenReturn(Optional.of(RevocationReason.CASCADE));

        assertThatThrownBy(() -> service.refresh(RAW_TOKEN)).isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("si la razón de revocación ya no existe, rechaza y no emite")
    void graceRetry_rejectsWhenReasonIsMissing() {
        when(refreshTokenRepository.findRevocationReason(rotated.getTokenHash())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh(RAW_TOKEN)).isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("si la razón sigue siendo ROTATION, emite un token nuevo tras tomar el lock del usuario")
    void graceRetry_issuesWhenReasonIsStillRotation() {
        when(refreshTokenRepository.findRevocationReason(rotated.getTokenHash()))
                .thenReturn(Optional.of(RevocationReason.ROTATION));

        RefreshTokenService.RefreshResult result = service.refresh(RAW_TOKEN);

        assertThat(result.refreshToken().rawToken()).isNotBlank().isNotEqualTo(RAW_TOKEN);
        InOrder order = inOrder(userRepository, refreshTokenRepository);
        order.verify(userRepository).findByIdForUpdate(USER_ID);
        order.verify(refreshTokenRepository).findRevocationReason(rotated.getTokenHash());
        order.verify(refreshTokenRepository).save(any(RefreshToken.class));
    }
}
