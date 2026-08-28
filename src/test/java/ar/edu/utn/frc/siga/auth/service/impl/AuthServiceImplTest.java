package ar.edu.utn.frc.siga.auth.service.impl;

import ar.edu.utn.frc.siga.auth.config.AuthDomainProperties;
import ar.edu.utn.frc.siga.auth.dto.request.LoginRequest;
import ar.edu.utn.frc.siga.auth.dto.request.RefreshTokenRequest;
import ar.edu.utn.frc.siga.auth.dto.response.TokenResponse;
import ar.edu.utn.frc.siga.auth.exception.InvalidCredentialsException;
import ar.edu.utn.frc.siga.auth.exception.LoginRateLimitExceededException;
import ar.edu.utn.frc.siga.auth.model.Role;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.security.JwtService;
import ar.edu.utn.frc.siga.auth.security.LoginRateLimiter;
import ar.edu.utn.frc.siga.auth.security.SecurityUser;
import ar.edu.utn.frc.siga.auth.service.RefreshTokenService;
import ar.edu.utn.frc.siga.auth.service.RefreshTokenService.IssuedRefreshToken;
import ar.edu.utn.frc.siga.auth.service.RefreshTokenService.RefreshResult;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl")
class AuthServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private LoginRateLimiter loginRateLimiter;
    @Mock
    private AuthDomainProperties authDomainProperties;
    @Mock
    private HttpServletRequest httpRequest;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(authenticationManager, userRepository, jwtService,
                refreshTokenService, loginRateLimiter, authDomainProperties);
        lenient().when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    private LoginRequest loginRequest(String email) {
        return new LoginRequest(email, "clave-secreta");
    }

    private User user(String email) {
        return User.builder().id(1L).email(email).roles(Set.of(Role.SUBSECRETARIA)).build();
    }

    @Test
    @DisplayName("login: credenciales válidas → emite tokens y registra el éxito en el rate limiter")
    void loginFeliz() {
        LoginRequest request = loginRequest("user@frc.utn.edu.ar");
        when(authDomainProperties.isAllowedEmail("user@frc.utn.edu.ar")).thenReturn(true);
        when(loginRateLimiter.isRateLimited(any(), any())).thenReturn(false);
        SecurityUser securityUser = SecurityUser.fromUser(user("user@frc.utn.edu.ar"));
        Authentication authentication = new UsernamePasswordAuthenticationToken(securityUser, null);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        User user = user("user@frc.utn.edu.ar");
        when(userRepository.findByEmailAndEnabledTrue("user@frc.utn.edu.ar")).thenReturn(Optional.of(user));
        when(refreshTokenService.issue(user)).thenReturn(new IssuedRefreshToken("raw-token", 3600));
        when(jwtService.generateAccessToken(any(), any())).thenReturn("access-token");
        when(jwtService.getAccessExpirationSeconds()).thenReturn(1200L);

        TokenResponse result = service.login(request, httpRequest);

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("raw-token");
        assertThat(result.getEmail()).isEqualTo("user@frc.utn.edu.ar");
        verify(loginRateLimiter).recordSuccess("user@frc.utn.edu.ar");
        verify(loginRateLimiter, never()).recordFailure(any(), any());
    }

    @Test
    @DisplayName("login: dominio no institucional → InvalidCredentialsException, no consulta rate limiter ni autentica")
    void loginDominioNoInstitucional() {
        when(authDomainProperties.isAllowedEmail("ajeno@gmail.com")).thenReturn(false);

        assertThatThrownBy(() -> service.login(loginRequest("ajeno@gmail.com"), httpRequest))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginRateLimiter, never()).isRateLimited(any(), any());
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("login: rate limit excedido → LoginRateLimitExceededException, no autentica")
    void loginRateLimitExcedido() {
        when(authDomainProperties.isAllowedEmail(any())).thenReturn(true);
        when(loginRateLimiter.isRateLimited(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.login(loginRequest("user@frc.utn.edu.ar"), httpRequest))
                .isInstanceOf(LoginRateLimitExceededException.class);

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("login: credenciales inválidas → InvalidCredentialsException y registra el fallo")
    void loginCredencialesInvalidas() {
        when(authDomainProperties.isAllowedEmail(any())).thenReturn(true);
        when(loginRateLimiter.isRateLimited(any(), any())).thenReturn(false);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("mal"));

        assertThatThrownBy(() -> service.login(loginRequest("user@frc.utn.edu.ar"), httpRequest))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginRateLimiter).recordFailure(any(), any());
        verify(loginRateLimiter, never()).recordSuccess(any());
    }

    @Test
    @DisplayName("login: autenticación ok pero usuario deshabilitado/eliminado → InvalidCredentialsException")
    void loginUsuarioDeshabilitado() {
        when(authDomainProperties.isAllowedEmail(any())).thenReturn(true);
        when(loginRateLimiter.isRateLimited(any(), any())).thenReturn(false);
        SecurityUser securityUser = SecurityUser.fromUser(user("user@frc.utn.edu.ar"));
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(securityUser, null));
        when(userRepository.findByEmailAndEnabledTrue("user@frc.utn.edu.ar")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(loginRequest("user@frc.utn.edu.ar"), httpRequest))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("refresh: delega en RefreshTokenService y compone la respuesta con el usuario devuelto")
    void refreshDelegaEnRefreshTokenService() {
        User user = user("user@frc.utn.edu.ar");
        IssuedRefreshToken issued = new IssuedRefreshToken("nuevo-raw", 3600);
        when(refreshTokenService.refresh("viejo-raw")).thenReturn(new RefreshResult(user, issued));
        when(jwtService.generateAccessToken(any(), any())).thenReturn("access-token");
        when(jwtService.getAccessExpirationSeconds()).thenReturn(1200L);

        TokenResponse result = service.refresh(new RefreshTokenRequest("viejo-raw"));

        assertThat(result.getRefreshToken()).isEqualTo("nuevo-raw");
        assertThat(result.getEmail()).isEqualTo("user@frc.utn.edu.ar");
    }

    @Test
    @DisplayName("logout: delega en RefreshTokenService.revoke")
    void logoutDelegaEnRevoke() {
        service.logout(new RefreshTokenRequest("raw-token"));

        verify(refreshTokenService).revoke("raw-token");
    }
}
