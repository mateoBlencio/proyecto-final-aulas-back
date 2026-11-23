package ar.edu.utn.frc.siga.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.auth.config.JwtProperties;
import ar.edu.utn.frc.siga.auth.exception.InvalidCredentialsException;
import ar.edu.utn.frc.siga.auth.model.RefreshToken;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.RefreshTokenRepository;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.security.OpaqueTokens;
import ar.edu.utn.frc.siga.auth.service.RefreshTokenService.IssuedRefreshToken;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenServiceImpl.issueForLogin")
class RefreshTokenServiceImplIssueForLoginTest {

    private static final long USER_ID = 7L;
    private static final String AUTHENTICATED_HASH = "hash-con-el-que-se-autentico";

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserRepository userRepository;

    private RefreshTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-fixed-secret-not-for-prod-0123456789abcdef");
        jwtProperties.setRefreshExpirationDays(30);
        service = new RefreshTokenServiceImpl(refreshTokenRepository, jwtProperties, userRepository);
    }

    private User user(String passwordHash, boolean enabled) {
        return User.builder().id(USER_ID).email("ana@frc.utn.edu.ar").passwordHash(passwordHash).enabled(enabled).build();
    }

    @Test
    @DisplayName("hash vigente igual al autenticado: toma el lock del usuario y emite el token")
    void sameHashIssuesTheTokenAfterLockingTheUser() {
        User locked = user(AUTHENTICATED_HASH, true);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(locked));

        IssuedRefreshToken issued = service.issueForLogin(user(AUTHENTICATED_HASH, true), AUTHENTICATED_HASH);

        InOrder order = inOrder(userRepository, refreshTokenRepository);
        order.verify(userRepository).findByIdForUpdate(USER_ID);
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        order.verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(locked);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(OpaqueTokens.hash(issued.rawToken()));
        assertThat(saved.getValue().getRevoked()).isFalse();
        assertThat(issued.expiresInSeconds()).isEqualTo(30L * 24 * 3600);
    }

    @Test
    @DisplayName("hash vigente distinto del autenticado: InvalidCredentialsException y no guarda token")
    void differentHashThrowsWithoutSaving() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user("hash-nuevo", true)));

        assertThatThrownBy(() -> service.issueForLogin(user(AUTHENTICATED_HASH, true), AUTHENTICATED_HASH))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("usuario inhabilitado con el mismo hash: InvalidCredentialsException y no guarda token")
    void disabledUserThrowsWithoutSaving() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(AUTHENTICATED_HASH, false)));

        assertThatThrownBy(() -> service.issueForLogin(user(AUTHENTICATED_HASH, true), AUTHENTICATED_HASH))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("usuario borrado entre la autenticación y el lock: InvalidCredentialsException y no guarda token")
    void missingUserThrowsWithoutSaving() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issueForLogin(user(AUTHENTICATED_HASH, true), AUTHENTICATED_HASH))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    @DisplayName("issue() no toma el lock del usuario")
    void plainIssueDoesNotLockTheUser() {
        service.issue(user(AUTHENTICATED_HASH, true));

        verify(refreshTokenRepository).save(any());
        verifyNoInteractions(userRepository);
    }
}
