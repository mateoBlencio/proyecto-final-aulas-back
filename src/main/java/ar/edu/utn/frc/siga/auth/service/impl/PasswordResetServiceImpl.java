package ar.edu.utn.frc.siga.auth.service.impl;

import ar.edu.utn.frc.siga.audit.AuditOperation;
import ar.edu.utn.frc.siga.auth.config.AuthDomainProperties;
import ar.edu.utn.frc.siga.auth.config.PasswordResetProperties;
import ar.edu.utn.frc.siga.auth.exception.InvalidResetTokenException;
import ar.edu.utn.frc.siga.auth.model.PasswordResetToken;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.PasswordResetTokenRepository;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.security.OpaqueTokens;
import ar.edu.utn.frc.siga.auth.security.PasswordResetRateLimiter;
import ar.edu.utn.frc.siga.auth.service.PasswordResetService;
import ar.edu.utn.frc.siga.auth.service.RefreshTokenService;
import ar.edu.utn.frc.siga.common.util.Zones;
import ar.edu.utn.frc.siga.notification.api.NotificationRecipient;
import ar.edu.utn.frc.siga.notification.api.NotificationRequest;
import ar.edu.utn.frc.siga.notification.api.NotificationSender;
import ar.edu.utn.frc.siga.notification.api.NotificationTemplate;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetRateLimiter rateLimiter;
    private final PasswordResetProperties properties;
    private final AuthDomainProperties authDomainProperties;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final NotificationSender notificationSender;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public PasswordResetServiceImpl(PasswordResetTokenRepository tokenRepository, UserRepository userRepository,
                                    RefreshTokenService refreshTokenService, PasswordResetRateLimiter rateLimiter,
                                    PasswordResetProperties properties, AuthDomainProperties authDomainProperties,
                                    PasswordPolicy passwordPolicy, PasswordEncoder passwordEncoder,
                                    NotificationSender notificationSender,
                                    ApplicationEventPublisher eventPublisher,
                                    PlatformTransactionManager transactionManager) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.authDomainProperties = authDomainProperties;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.notificationSender = notificationSender;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void requestReset(String email, String ip) {
        String normalized = AuthDomainProperties.normalizeEmail(email);
        rateLimiter.checkForgot(normalized, ip);
        eventPublisher.publishEvent(new PasswordResetRequested(normalized));
    }

    @Override
    @Transactional
    public void issueResetLink(String normalized) {
        Optional<User> user = authDomainProperties.isAllowedEmail(normalized)
                ? userRepository.findByEmailAndEnabledTrue(normalized)
                : Optional.empty();
        if (user.isEmpty()) {
            log.debug("Recuperación de contraseña sin usuario habilitado");
            return;
        }

        User target = user.get();
        tokenRepository.deleteByUserId(target.getId());
        String rawToken = OpaqueTokens.generate();
        PasswordResetToken saved = tokenRepository.saveAndFlush(PasswordResetToken.builder()
                .user(target)
                .tokenHash(OpaqueTokens.hash(rawToken))
                .expiresAt(Instant.now().plusSeconds(properties.getTokenTtlMinutes() * 60L))
                .build());

        notificationSender.send(new NotificationRequest(
                NotificationTemplate.PASSWORD_RESET,
                List.of(new NotificationRecipient(fullName(target), target.getEmail())),
                Map.of("nombre", fullName(target),
                        "enlace", properties.getFrontendBaseUrl() + "/restablecer-contrasena#token=" + rawToken,
                        "minutosVencimiento", properties.getTokenTtlMinutes()),
                "password-reset:" + saved.getId()));
        log.info("Enlace de recuperación de contraseña emitido: userId={}", target.getId());
    }

    @Override
    @AuditOperation(value = "Restablecimiento de contraseña", subject = "User")
    public void reset(String rawToken, String newPassword, String ip) {
        rateLimiter.checkReset(ip);

        String tokenHash = OpaqueTokens.hash(rawToken);
        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
                .filter(candidate -> isUsable(candidate, Instant.now()))
                .orElseThrow(InvalidResetTokenException::new);
        User user = token.getUser();
        passwordPolicy.validate("newPassword", newPassword, user.getEmail(), user.getFirstName(), user.getLastName());
        String encoded = passwordEncoder.encode(newPassword);

        transactionTemplate.executeWithoutResult(status -> {
            Instant now = Instant.now();
            userRepository.findByIdForUpdate(user.getId());
            PasswordResetToken locked = tokenRepository.findByTokenHashForUpdate(tokenHash)
                    .filter(candidate -> isUsable(candidate, now))
                    .orElseThrow(InvalidResetTokenException::new);
            User target = locked.getUser();
            target.setPasswordHash(encoded);
            userRepository.save(target);
            locked.setUsedAt(now);
            tokenRepository.save(locked);
            tokenRepository.deleteByUserIdExcept(target.getId(), locked.getId());
            refreshTokenService.revokeAllByUserId(target.getId());
            notificationSender.send(new NotificationRequest(
                    NotificationTemplate.PASSWORD_CHANGED,
                    List.of(new NotificationRecipient(fullName(target), target.getEmail())),
                    Map.of("nombre", fullName(target),
                            "fechaHora", DATE_TIME.format(now.atZone(Zones.LOCAL))),
                    "password-changed:" + locked.getId()));
            log.info("Contraseña restablecida: userId={}", target.getId());
        });
    }

    @Override
    @Transactional
    public int purgeExpired() {
        return tokenRepository.deleteExpiredBefore(Instant.now());
    }

    private static boolean isUsable(PasswordResetToken token, Instant now) {
        return token.getUsedAt() == null
                && token.getExpiresAt().isAfter(now)
                && Boolean.TRUE.equals(token.getUser().getEnabled());
    }

    private static String fullName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }
}
