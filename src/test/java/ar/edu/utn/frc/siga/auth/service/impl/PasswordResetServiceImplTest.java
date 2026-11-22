package ar.edu.utn.frc.siga.auth.service.impl;

import ar.edu.utn.frc.siga.auth.config.AuthDomainProperties;
import ar.edu.utn.frc.siga.auth.config.PasswordResetProperties;
import ar.edu.utn.frc.siga.auth.exception.FieldValidationException;
import ar.edu.utn.frc.siga.auth.exception.InvalidResetTokenException;
import ar.edu.utn.frc.siga.auth.exception.TooManyRequestsException;
import ar.edu.utn.frc.siga.auth.model.PasswordResetToken;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.PasswordResetTokenRepository;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.security.OpaqueTokens;
import ar.edu.utn.frc.siga.auth.security.PasswordResetRateLimiter;
import ar.edu.utn.frc.siga.auth.service.RefreshTokenService;
import ar.edu.utn.frc.siga.notification.api.NotificationRequest;
import ar.edu.utn.frc.siga.notification.api.NotificationSender;
import ar.edu.utn.frc.siga.notification.api.NotificationTemplate;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetServiceImpl")
class PasswordResetServiceImplTest {

    private static final String IP = "10.0.0.7";
    private static final String EMAIL = "ana.gomez@frc.utn.edu.ar";
    private static final String RAW_TOKEN = "token-crudo-de-prueba";
    private static final String NEW_PASSWORD = "Cuaderno-Violeta-7391";

    @Mock
    private PasswordResetTokenRepository tokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private PasswordResetRateLimiter rateLimiter;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private NotificationSender notificationSender;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    private PasswordResetServiceImpl service;

    @BeforeEach
    void setUp() {
        PasswordResetProperties properties = new PasswordResetProperties();
        properties.setFrontendBaseUrl("https://siga.frc.utn.edu.ar");
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        service = new PasswordResetServiceImpl(tokenRepository, userRepository, refreshTokenService, rateLimiter,
                properties, new AuthDomainProperties(), new PasswordPolicy(), passwordEncoder, notificationSender, eventPublisher,
                transactionManager);
    }

    @Test
    @DisplayName("requestReset: consulta el límite y publica el evento con el correo normalizado, sin tocar usuarios")
    void requestResetChecksTheLimitAndPublishesTheNormalizedEmail() {
        service.requestReset("  Ana.Gomez@FRC.utn.edu.ar ", IP);

        InOrder order = inOrder(rateLimiter, eventPublisher);
        order.verify(rateLimiter).checkForgot(EMAIL, IP);
        order.verify(eventPublisher).publishEvent(new PasswordResetRequested(EMAIL));
        verifyNoInteractions(userRepository, tokenRepository, notificationSender);
    }

    @Test
    @DisplayName("requestReset publica el evento aunque el correo sea inexistente o de dominio ajeno")
    void requestResetPublishesForUnknownAndForeignEmails() {
        service.requestReset("nadie@frc.utn.edu.ar", IP);
        service.requestReset("ana@gmail.com", IP);

        verify(eventPublisher).publishEvent(new PasswordResetRequested("nadie@frc.utn.edu.ar"));
        verify(eventPublisher).publishEvent(new PasswordResetRequested("ana@gmail.com"));
        verifyNoInteractions(userRepository, tokenRepository, notificationSender);
    }

    @Test
    @DisplayName("el límite excedido corta antes de publicar el evento")
    void rateLimitBlocksBeforePublishing() {
        doThrow(new TooManyRequestsException(Duration.ofMinutes(5))).when(rateLimiter).checkForgot(EMAIL, IP);

        assertThatThrownBy(() -> service.requestReset(EMAIL, IP)).isInstanceOf(TooManyRequestsException.class);

        verifyNoInteractions(eventPublisher, userRepository, notificationSender);
    }

    @Test
    @DisplayName("issueResetLink con correo inexistente: no guarda token ni manda nada")
    void unknownEmailDoesNothing() {
        when(userRepository.findByEmailAndEnabledTrue("nadie@frc.utn.edu.ar")).thenReturn(Optional.empty());

        service.issueResetLink("nadie@frc.utn.edu.ar");

        verifyNoInteractions(notificationSender);
        verify(tokenRepository, never()).saveAndFlush(any());
        verify(tokenRepository, never()).deleteByUserId(anyLong());
    }

    @Test
    @DisplayName("issueResetLink con correo inhabilitado: el repositorio no lo devuelve, no guarda token ni manda nada")
    void disabledUserDoesNothing() {
        when(userRepository.findByEmailAndEnabledTrue(EMAIL)).thenReturn(Optional.empty());

        service.issueResetLink(EMAIL);

        verifyNoInteractions(notificationSender);
        verify(tokenRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("issueResetLink con correo de dominio ajeno: ni siquiera busca al usuario")
    void foreignDomainDoesNothing() {
        service.issueResetLink("ana@gmail.com");

        verifyNoInteractions(userRepository, notificationSender);
        verify(tokenRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("issueResetLink con correo válido: borra los tokens previos, guarda el hash y manda el enlace con #token=")
    void validEmailStoresHashDeletesPreviousAndSendsLink() {
        User user = user(7L, true);
        when(userRepository.findByEmailAndEnabledTrue(EMAIL)).thenReturn(Optional.of(user));
        when(tokenRepository.saveAndFlush(any(PasswordResetToken.class))).thenAnswer(invocation -> {
            PasswordResetToken token = invocation.getArgument(0);
            token.setId(99L);
            return token;
        });
        Instant before = Instant.now();

        service.issueResetLink(EMAIL);

        InOrder order = inOrder(tokenRepository);
        order.verify(tokenRepository).deleteByUserId(7L);
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        order.verify(tokenRepository).saveAndFlush(saved.capture());
        ArgumentCaptor<NotificationRequest> sent = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationSender).send(sent.capture());

        String link = (String) sent.getValue().model().get("enlace");
        String rawToken = link.substring(link.indexOf("#token=") + "#token=".length());
        assertThat(link).startsWith("https://siga.frc.utn.edu.ar/restablecer-contrasena#token=");
        assertThat(rawToken).hasSize(43);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(OpaqueTokens.hash(rawToken)).isNotEqualTo(rawToken);
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(saved.getValue().getUsedAt()).isNull();
        assertThat(saved.getValue().getExpiresAt())
                .isBetween(before.plus(Duration.ofMinutes(30)), Instant.now().plus(Duration.ofMinutes(30)));
        assertThat(sent.getValue().template()).isEqualTo(NotificationTemplate.PASSWORD_RESET);
        assertThat(sent.getValue().recipients()).singleElement()
                .satisfies(recipient -> assertThat(recipient.email()).isEqualTo(EMAIL));
        assertThat(sent.getValue().model()).containsEntry("nombre", "Ana Gómez")
                .containsEntry("minutosVencimiento", 30);
        assertThat(sent.getValue().idempotencyKey()).isEqualTo("password-reset:99");
    }

    @Test
    @DisplayName("reset con token inexistente lanza InvalidResetTokenException")
    void resetWithUnknownTokenThrows() {
        when(tokenRepository.findByTokenHash(OpaqueTokens.hash(RAW_TOKEN))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reset(RAW_TOKEN, NEW_PASSWORD, IP))
                .isInstanceOf(InvalidResetTokenException.class);

        verifyNothingWritten();
    }

    @Test
    @DisplayName("reset con token usado lanza InvalidResetTokenException")
    void resetWithUsedTokenThrows() {
        PasswordResetToken token = token(user(7L, true), Instant.now().plusSeconds(600));
        token.setUsedAt(Instant.now().minusSeconds(10));
        when(tokenRepository.findByTokenHash(OpaqueTokens.hash(RAW_TOKEN))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.reset(RAW_TOKEN, NEW_PASSWORD, IP))
                .isInstanceOf(InvalidResetTokenException.class);

        verifyNothingWritten();
    }

    @Test
    @DisplayName("reset con token vencido lanza InvalidResetTokenException")
    void resetWithExpiredTokenThrows() {
        PasswordResetToken token = token(user(7L, true), Instant.now().minusSeconds(1));
        when(tokenRepository.findByTokenHash(OpaqueTokens.hash(RAW_TOKEN))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.reset(RAW_TOKEN, NEW_PASSWORD, IP))
                .isInstanceOf(InvalidResetTokenException.class);

        verifyNothingWritten();
    }

    @Test
    @DisplayName("reset con token de un usuario inhabilitado lanza InvalidResetTokenException")
    void resetWithDisabledUserThrows() {
        PasswordResetToken token = token(user(7L, false), Instant.now().plusSeconds(600));
        when(tokenRepository.findByTokenHash(OpaqueTokens.hash(RAW_TOKEN))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.reset(RAW_TOKEN, NEW_PASSWORD, IP))
                .isInstanceOf(InvalidResetTokenException.class);

        verifyNothingWritten();
    }

    @Test
    @DisplayName("reset consulta el límite por IP antes de mirar el token")
    void resetChecksTheRateLimitFirst() {
        doThrow(new TooManyRequestsException(Duration.ofMinutes(5))).when(rateLimiter).checkReset(IP);

        assertThatThrownBy(() -> service.reset(RAW_TOKEN, NEW_PASSWORD, IP))
                .isInstanceOf(TooManyRequestsException.class);

        verifyNoInteractions(tokenRepository);
    }

    @Test
    @DisplayName("contraseña que rompe la política: lanza con newPassword y no marca fecha_uso")
    void resetWithWeakPasswordLeavesTokenUsable() {
        PasswordResetToken token = token(user(7L, true), Instant.now().plusSeconds(600));
        when(tokenRepository.findByTokenHash(OpaqueTokens.hash(RAW_TOKEN))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.reset(RAW_TOKEN, "corta", IP))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(e.getProperties().get("errors").toString()).contains("newPassword"));

        assertThat(token.getUsedAt()).isNull();
        verifyNothingWritten();
    }

    @Test
    @DisplayName("reset válido: guarda la clave codificada, marca el uso, revoca refresh tokens y avisa el cambio")
    void validResetChangesPasswordMarksUseAndRevokesSessions() {
        User user = user(7L, true);
        PasswordResetToken token = token(user, Instant.now().plusSeconds(600));
        token.setId(55L);
        String hash = OpaqueTokens.hash(RAW_TOKEN);
        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));
        when(tokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(token));
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("hash-nuevo");
        Instant before = Instant.now();

        service.reset(RAW_TOKEN, NEW_PASSWORD, IP);

        assertThat(user.getPasswordHash()).isEqualTo("hash-nuevo");
        assertThat(token.getUsedAt()).isBetween(before, Instant.now());
        verify(rateLimiter).checkReset(IP);
        verify(userRepository).save(user);
        verify(tokenRepository).save(token);
        verify(tokenRepository).deleteByUserIdExcept(7L, 55L);
        verify(refreshTokenService).revokeAllByUserId(7L);
        verify(transactionManager).commit(transactionStatus);
        ArgumentCaptor<NotificationRequest> sent = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationSender).send(sent.capture());
        assertThat(sent.getValue().template()).isEqualTo(NotificationTemplate.PASSWORD_CHANGED);
        assertThat(sent.getValue().model()).containsKeys("nombre", "fechaHora");
        assertThat(sent.getValue().idempotencyKey()).isEqualTo("password-changed:55");
    }

    @Test
    @DisplayName("si otro pedido consumió el token entre la lectura y el lock, lanza y no revoca ni avisa")
    void resetLosingTheRaceThrowsAndRollsBack() {
        User user = user(7L, true);
        PasswordResetToken fresh = token(user, Instant.now().plusSeconds(600));
        PasswordResetToken consumed = token(user, Instant.now().plusSeconds(600));
        consumed.setUsedAt(Instant.now());
        String hash = OpaqueTokens.hash(RAW_TOKEN);
        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(fresh));
        when(tokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(consumed));
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("hash-nuevo");

        assertThatThrownBy(() -> service.reset(RAW_TOKEN, NEW_PASSWORD, IP))
                .isInstanceOf(InvalidResetTokenException.class);

        assertThat(user.getPasswordHash()).isEqualTo("hash-viejo");
        verify(transactionManager).rollback(transactionStatus);
        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllByUserId(anyLong());
        verifyNoInteractions(notificationSender);
    }

    @Test
    @DisplayName("purgeExpired devuelve la cantidad borrada por el repositorio")
    void purgeExpiredDelegatesToTheRepository() {
        when(tokenRepository.deleteExpiredBefore(any(Instant.class))).thenReturn(4);

        assertThat(service.purgeExpired()).isEqualTo(4);
    }

    private void verifyNothingWritten() {
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllByUserId(anyLong());
        verify(tokenRepository, never()).findByTokenHashForUpdate(anyString());
        verifyNoInteractions(notificationSender);
        verify(transactionManager, never()).getTransaction(any());
    }

    private static User user(Long id, boolean enabled) {
        return User.builder().id(id).email(EMAIL).passwordHash("hash-viejo").enabled(enabled)
                .firstName("Ana").lastName("Gómez").build();
    }

    private static PasswordResetToken token(User user, Instant expiresAt) {
        return PasswordResetToken.builder().user(user).tokenHash(OpaqueTokens.hash(RAW_TOKEN))
                .expiresAt(expiresAt).build();
    }
}
