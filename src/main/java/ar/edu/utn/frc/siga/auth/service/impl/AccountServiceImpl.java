package ar.edu.utn.frc.siga.auth.service.impl;

import ar.edu.utn.frc.siga.audit.AuditOperation;
import ar.edu.utn.frc.siga.auth.dto.request.ChangePasswordRequest;
import ar.edu.utn.frc.siga.auth.dto.request.UpdateProfileRequest;
import ar.edu.utn.frc.siga.auth.dto.response.MeResponseDto;
import ar.edu.utn.frc.siga.auth.dto.response.PermissionGrantDto;
import ar.edu.utn.frc.siga.auth.dto.response.TokenResponse;
import ar.edu.utn.frc.siga.auth.exception.FieldValidationException;
import ar.edu.utn.frc.siga.auth.exception.TooManyRequestsException;
import ar.edu.utn.frc.siga.auth.mapper.RoleAssignmentComposer;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.security.LoginRateLimiter;
import ar.edu.utn.frc.siga.auth.security.SecurityUser;
import ar.edu.utn.frc.siga.auth.service.AccountService;
import ar.edu.utn.frc.siga.auth.service.AuthService;
import ar.edu.utn.frc.siga.auth.service.RefreshTokenService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.common.util.Zones;
import ar.edu.utn.frc.siga.notification.api.NotificationRecipient;
import ar.edu.utn.frc.siga.notification.api.NotificationRequest;
import ar.edu.utn.frc.siga.notification.api.NotificationSender;
import ar.edu.utn.frc.siga.notification.api.NotificationTemplate;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class AccountServiceImpl implements AccountService {

    private static final int MAX_PASSWORD_BYTES = 72;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final UserRepository userRepository;
    private final RoleAssignmentComposer roleAssignmentComposer;
    private final LoginRateLimiter loginRateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final RefreshTokenService refreshTokenService;
    private final AuthService authService;
    private final NotificationSender notificationSender;
    private final TransactionTemplate transactionTemplate;

    public AccountServiceImpl(UserRepository userRepository, RoleAssignmentComposer roleAssignmentComposer,
                              LoginRateLimiter loginRateLimiter, PasswordEncoder passwordEncoder,
                              PasswordPolicy passwordPolicy, RefreshTokenService refreshTokenService,
                              AuthService authService, NotificationSender notificationSender,
                              PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.roleAssignmentComposer = roleAssignmentComposer;
        this.loginRateLimiter = loginRateLimiter;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.refreshTokenService = refreshTokenService;
        this.authService = authService;
        this.notificationSender = notificationSender;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    @Transactional(readOnly = true)
    public MeResponseDto me(SecurityUser principal) {
        return toMe(principal, findEnabled(principal.getEmail()));
    }

    @Override
    @Transactional
    @AuditOperation(value = "Edición de perfil propio", subject = "User")
    public MeResponseDto updateProfile(SecurityUser principal, UpdateProfileRequest request) {
        String firstName = cleanName("firstName", request.firstName());
        String lastName = cleanName("lastName", request.lastName());
        User user = findEnabled(principal.getEmail());
        user.setFirstName(firstName);
        user.setLastName(lastName);
        User saved = userRepository.save(user);
        log.info("Perfil propio actualizado: userId={}", saved.getId());
        return toMe(principal, saved);
    }

    @Override
    @AuditOperation(value = "Cambio de contraseña propia", subject = "User")
    public TokenResponse changePassword(SecurityUser principal, ChangePasswordRequest request, String ip) {
        String email = principal.getEmail();
        Instant now = Instant.now();
        if (loginRateLimiter.isRateLimited(email, ip, now)) {
            log.warn("Cambio de contraseña rechazado por rate limit: userId={}, ip={}", principal.getId(), ip);
            throw new TooManyRequestsException(loginRateLimiter.retryAfter(email, ip, now));
        }

        User user = findEnabled(email);
        if (!currentPasswordMatches(request.currentPassword(), user)) {
            loginRateLimiter.recordFailure(email, ip, Instant.now());
            log.warn("Cambio de contraseña con contraseña actual incorrecta: userId={}, ip={}", user.getId(), ip);
            throw new FieldValidationException("currentPassword", "La contraseña actual no es correcta.");
        }
        loginRateLimiter.recordSuccess(email, ip);

        if (request.newPassword().equals(request.currentPassword())) {
            throw new FieldValidationException("newPassword", "La contraseña nueva tiene que ser distinta de la actual.");
        }
        passwordPolicy.validate("newPassword", request.newPassword(), user.getEmail(), user.getFirstName(),
                user.getLastName());
        String encoded = passwordEncoder.encode(request.newPassword());

        transactionTemplate.executeWithoutResult(status -> {
            User managed = userRepository.findById(user.getId())
                    .orElseThrow(() -> ResourceNotFoundException.of("User", user.getId()));
            managed.setPasswordHash(encoded);
            userRepository.save(managed);
            refreshTokenService.revokeAllByUserId(managed.getId());
            notificationSender.send(new NotificationRequest(
                    NotificationTemplate.PASSWORD_CHANGED,
                    List.of(new NotificationRecipient(fullName(managed), managed.getEmail())),
                    Map.of("nombre", fullName(managed),
                            "fechaHora", DATE_TIME.format(Instant.now().atZone(Zones.LOCAL))),
                    "password-changed:" + managed.getId() + ":" + UUID.randomUUID()));
        });
        log.info("Contraseña propia cambiada: userId={}", user.getId());

        return authService.startSession(user);
    }

    private boolean currentPasswordMatches(String currentPassword, User user) {
        return currentPassword.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES
                && passwordEncoder.matches(currentPassword, user.getPasswordHash());
    }

    private User findEnabled(String email) {
        return userRepository.findByEmailAndEnabledTrue(email)
                .orElseThrow(() -> ResourceNotFoundException.of("User", email));
    }

    private static String cleanName(String field, String value) {
        String stripped = value.strip();
        if (stripped.codePoints().anyMatch(Character::isISOControl)) {
            throw new FieldValidationException(field, "No puede contener caracteres de control.");
        }
        return stripped;
    }

    private MeResponseDto toMe(SecurityUser principal, User user) {
        return new MeResponseDto(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                roleAssignmentComposer.composeAll(user.getRoleAssignments()), permissionsOf(principal));
    }

    private static List<PermissionGrantDto> permissionsOf(SecurityUser principal) {
        return principal.getScopesByPermission().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().name()))
                .map(entry -> grant(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static PermissionGrantDto grant(Permission permission, BuildingScope scope) {
        List<Long> buildingIds = scope.isUnrestricted() ? List.of() : scope.buildingIds().stream().sorted().toList();
        return new PermissionGrantDto(permission.name(), scope.isUnrestricted(), buildingIds);
    }

    private static String fullName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }
}
