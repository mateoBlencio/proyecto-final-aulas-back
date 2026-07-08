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
import ar.edu.utn.frc.siga.auth.service.AuthService;
import ar.edu.utn.frc.siga.auth.service.RefreshTokenService;
import ar.edu.utn.frc.siga.auth.service.RefreshTokenService.IssuedRefreshToken;
import ar.edu.utn.frc.siga.auth.service.RefreshTokenService.RefreshResult;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginRateLimiter loginRateLimiter;
    private final AuthDomainProperties authDomainProperties;

    @Override
    @Transactional
    public TokenResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String email = request.email();
        String ip = httpRequest.getRemoteAddr();

        if (!email.toLowerCase().endsWith("@" + authDomainProperties.getAllowedEmailDomain().toLowerCase())) {
            log.warn("Login rechazado, dominio no institucional: email={}, ip={}", email, ip);
            throw new InvalidCredentialsException();
        }

        if (loginRateLimiter.isRateLimited(email, Instant.now())) {
            log.warn("Login rechazado por rate limit: email={}, ip={}", email, ip);
            throw new LoginRateLimitExceededException();
        }

        SecurityUser securityUser;
        try {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
            securityUser = (SecurityUser) authentication.getPrincipal();
        } catch (AuthenticationException e) {
            loginRateLimiter.recordFailure(email, Instant.now());
            log.warn("Login fallido: email={}, ip={}", email, ip);
            throw new InvalidCredentialsException();
        }

        loginRateLimiter.recordSuccess(email);
        log.info("Login exitoso: email={}, ip={}", email, ip);

        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(InvalidCredentialsException::new);

        return buildTokenResponse(user, refreshTokenService.issue(user));
    }

    @Override
    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {
        RefreshResult result = refreshTokenService.refresh(request.refreshToken());
        log.info("Refresh exitoso: email={}", result.user().getEmail());
        return buildTokenResponse(result.user(), result.refreshToken());
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
        log.info("Logout exitoso");
    }

    private TokenResponse buildTokenResponse(User user, IssuedRefreshToken refreshToken) {
        Set<Role> roles = user.getRoles();
        String accessToken = jwtService.generateAccessToken(user.getEmail(), roles);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .expiresInSeconds(jwtService.getAccessExpirationSeconds())
                .refreshToken(refreshToken.rawToken())
                .refreshExpiresInSeconds(refreshToken.expiresInSeconds())
                .email(user.getEmail())
                .roles(roles.stream().map(Enum::name).collect(Collectors.toSet()))
                .build();
    }
}
