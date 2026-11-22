package ar.edu.utn.frc.siga.auth.service.impl;

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
import ar.edu.utn.frc.siga.auth.service.AuthService;
import ar.edu.utn.frc.siga.auth.service.RefreshTokenService;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.notification.api.NotificationRequest;
import ar.edu.utn.frc.siga.notification.api.NotificationSender;
import ar.edu.utn.frc.siga.notification.api.NotificationTemplate;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountServiceImpl")
class AccountServiceImplTest {

    private static final String IP = "10.0.0.7";
    private static final String EMAIL = "ana.gomez@frc.utn.edu.ar";
    private static final String CURRENT = "Contrasena-Actual-5521";
    private static final String NEW_PASSWORD = "Cuaderno-Violeta-7391";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleAssignmentComposer roleAssignmentComposer;
    @Mock
    private LoginRateLimiter loginRateLimiter;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private AuthService authService;
    @Mock
    private NotificationSender notificationSender;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    private AccountServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        service = new AccountServiceImpl(userRepository, roleAssignmentComposer, loginRateLimiter, passwordEncoder,
                new PasswordPolicy(), refreshTokenService, authService, notificationSender, transactionManager);
    }

    @Test
    @DisplayName("contraseña actual incorrecta: falla sobre currentPassword, registra el fallo y no escribe nada")
    void wrongCurrentPasswordFailsAndRecordsFailure() {
        User user = user();
        when(userRepository.findByEmailAndEnabledTrue(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(CURRENT, "hash-viejo")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(principal(), new ChangePasswordRequest(CURRENT, NEW_PASSWORD), IP))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(errorFields(e)).containsExactly("currentPassword"));

        verify(loginRateLimiter).recordFailure(org.mockito.ArgumentMatchers.eq(EMAIL),
                org.mockito.ArgumentMatchers.eq(IP), any(Instant.class));
        verify(loginRateLimiter, never()).recordSuccess(anyString(), anyString());
        verifyNothingWritten();
    }

    @Test
    @DisplayName("límite alcanzado: lanza TooManyRequestsException sin comparar la contraseña ni buscar al usuario")
    void rateLimitedDoesNotCheckPassword() {
        when(loginRateLimiter.isRateLimited(org.mockito.ArgumentMatchers.eq(EMAIL),
                org.mockito.ArgumentMatchers.eq(IP), any(Instant.class))).thenReturn(true);
        when(loginRateLimiter.retryAfter(org.mockito.ArgumentMatchers.eq(EMAIL),
                org.mockito.ArgumentMatchers.eq(IP), any(Instant.class))).thenReturn(Duration.ofSeconds(90));

        assertThatThrownBy(() -> service.changePassword(principal(), new ChangePasswordRequest(CURRENT, NEW_PASSWORD), IP))
                .isInstanceOf(TooManyRequestsException.class);

        verify(passwordEncoder, never()).matches(any(), any());
        verifyNoInteractions(userRepository);
        verify(loginRateLimiter, never()).recordFailure(anyString(), anyString(), any());
        verifyNothingWritten();
    }

    @Test
    @DisplayName("contraseña nueva igual a la actual: falla sobre newPassword y no escribe nada")
    void sameAsCurrentFailsOnNewPassword() {
        when(userRepository.findByEmailAndEnabledTrue(EMAIL)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches(CURRENT, "hash-viejo")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(principal(), new ChangePasswordRequest(CURRENT, CURRENT), IP))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(errorFields(e)).containsExactly("newPassword"));

        verify(passwordEncoder, never()).encode(any());
        verifyNothingWritten();
    }

    @Test
    @DisplayName("contraseña nueva que incumple la política: falla sobre newPassword y no escribe nada")
    void policyViolationFailsOnNewPassword() {
        when(userRepository.findByEmailAndEnabledTrue(EMAIL)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches(CURRENT, "hash-viejo")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(principal(), new ChangePasswordRequest(CURRENT, "corta"), IP))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(errorFields(e)).containsExactly("newPassword"));

        verify(passwordEncoder, never()).encode(any());
        verifyNothingWritten();
    }

    @Test
    @DisplayName("éxito: guarda el hash nuevo, revoca los refresh tokens antes de abrir sesión y manda PASSWORD_CHANGED")
    void successRevokesBeforeStartSessionAndNotifies() {
        User user = user();
        TokenResponse session = TokenResponse.builder().accessToken("access").refreshToken("refresh").build();
        when(userRepository.findByEmailAndEnabledTrue(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(CURRENT, "hash-viejo")).thenReturn(true);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("hash-nuevo");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(authService.startSession(user)).thenReturn(session);

        TokenResponse result = service.changePassword(principal(), new ChangePasswordRequest(CURRENT, NEW_PASSWORD), IP);

        assertThat(result).isSameAs(session);
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hash-nuevo");
        verify(loginRateLimiter).recordSuccess(EMAIL, IP);
        ArgumentCaptor<NotificationRequest> sent = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationSender).send(sent.capture());
        assertThat(sent.getValue().template()).isEqualTo(NotificationTemplate.PASSWORD_CHANGED);
        assertThat(sent.getValue().recipients()).singleElement()
                .satisfies(recipient -> assertThat(recipient.email()).isEqualTo(EMAIL));
        InOrder order = inOrder(refreshTokenService, authService);
        order.verify(refreshTokenService).revokeAllByUserId(7L);
        order.verify(authService).startSession(user);
    }

    @Test
    @DisplayName("updateProfile: recorta los espacios de nombre y apellido antes de guardar")
    void updateProfileStripsNames() {
        User user = user();
        when(userRepository.findByEmailAndEnabledTrue(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleAssignmentComposer.composeAll(any())).thenReturn(List.of());

        MeResponseDto result = service.updateProfile(principal(), new UpdateProfileRequest("  Lucía ", "\tPérez  "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getFirstName()).isEqualTo("Lucía");
        assertThat(saved.getValue().getLastName()).isEqualTo("Pérez");
        assertThat(result.firstName()).isEqualTo("Lucía");
        assertThat(result.lastName()).isEqualTo("Pérez");
    }

    @Test
    @DisplayName("updateProfile: un carácter de control en el nombre falla sobre firstName y no guarda")
    void updateProfileRejectsControlCharsInFirstName() {
        assertThatThrownBy(() -> service.updateProfile(principal(), new UpdateProfileRequest("Lu\u0000cía", "Pérez")))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(errorFields(e)).containsExactly("firstName"));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateProfile: un carácter de control en el apellido falla sobre lastName y no guarda")
    void updateProfileRejectsControlCharsInLastName() {
        assertThatThrownBy(() -> service.updateProfile(principal(), new UpdateProfileRequest("Lucía", "Pé\nrez")))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(errorFields(e)).containsExactly("lastName"));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("me: ordena los permisos por nombre, los edificios por id y marca allBuildings solo si es irrestricto")
    void meSortsPermissionsAndBuildings() {
        when(userRepository.findByEmailAndEnabledTrue(EMAIL)).thenReturn(Optional.of(user()));
        when(roleAssignmentComposer.composeAll(any())).thenReturn(List.of());
        Map<Permission, BuildingScope> scopes = new LinkedHashMap<>();
        scopes.put(Permission.USER_READ, BuildingScope.unrestricted());
        scopes.put(Permission.CLASSROOM_UPDATE, BuildingScope.of(Set.of(30L, 5L, 12L)));
        scopes.put(Permission.BUILDING_READ, BuildingScope.of(Set.of(9L)));

        MeResponseDto me = service.me(new SecurityUser(7L, EMAIL, "hash-viejo", true, scopes));

        assertThat(me.id()).isEqualTo(7L);
        assertThat(me.email()).isEqualTo(EMAIL);
        assertThat(me.permissions()).containsExactly(
                new PermissionGrantDto("BUILDING_READ", false, List.of(9L)),
                new PermissionGrantDto("CLASSROOM_UPDATE", false, List.of(5L, 12L, 30L)),
                new PermissionGrantDto("USER_READ", true, List.of()));
    }

    private void verifyNothingWritten() {
        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllByUserId(anyLong());
        verifyNoInteractions(notificationSender, authService);
    }

    private static Set<String> errorFields(FieldValidationException exception) {
        @SuppressWarnings("unchecked")
        Map<String, List<String>> errors = (Map<String, List<String>>) exception.getProperties().get("errors");
        return errors.keySet();
    }

    private static SecurityUser principal() {
        return new SecurityUser(7L, EMAIL, "hash-viejo", true, Map.of());
    }

    private static User user() {
        return User.builder().id(7L).email(EMAIL).passwordHash("hash-viejo").enabled(true)
                .firstName("Ana").lastName("Gómez").build();
    }
}
