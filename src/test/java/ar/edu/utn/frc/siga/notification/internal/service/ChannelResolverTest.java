package ar.edu.utn.frc.siga.notification.internal.service;

import ar.edu.utn.frc.siga.notification.api.NotificationChannel;
import ar.edu.utn.frc.siga.notification.api.NotificationRecipient;
import ar.edu.utn.frc.siga.notification.api.NotificationRequest;
import ar.edu.utn.frc.siga.notification.api.NotificationResult;
import ar.edu.utn.frc.siga.notification.api.NotificationTemplate;
import ar.edu.utn.frc.siga.notification.internal.channel.ChannelRegistry;
import ar.edu.utn.frc.siga.notification.internal.channel.ChannelSender;
import ar.edu.utn.frc.siga.notification.internal.config.NotificationProperties;
import ar.edu.utn.frc.siga.notification.internal.model.Notification;
import ar.edu.utn.frc.siga.notification.internal.model.NotificationStatus;
import ar.edu.utn.frc.siga.notification.internal.render.NotificationRenderer;
import ar.edu.utn.frc.siga.notification.internal.render.RenderedNotification;
import ar.edu.utn.frc.siga.notification.internal.repository.NotificationRepository;
import ar.edu.utn.frc.siga.settings.api.SettingsReader;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelResolverTest {

    @Mock
    private SettingsReader settingsReader;
    @Mock
    private NotificationRenderer renderer;
    @Mock
    private NotificationRepository repository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final NotificationProperties properties = new NotificationProperties();

    private NotificationRequest request;

    @BeforeEach
    void setUp() {
        request = new NotificationRequest(
                NotificationTemplate.ROOM_REQUEST_RESOLVED,
                List.of(new NotificationRecipient("Ana Gómez", "ana@frc.utn.edu.ar")),
                Map.of(),
                "room-request-item:1:RESOLVED");
    }

    @Test
    void notificationsDisabledSkipsEveryPairWithoutTouchingRepository() {
        when(settingsReader.getBoolean(SettingKey.NOTIFICATIONS_ENABLED)).thenReturn(false);
        NotificationSenderImpl sender = new NotificationSenderImpl(
                settingsReader, new ChannelRegistry(List.of()), renderer, repository, eventPublisher, properties);

        NotificationResult result = sender.send(request);

        assertThat(result).isEqualTo(new NotificationResult(0, NotificationChannel.values().length));
        verifyNoInteractions(repository, eventPublisher);
    }

    @Test
    void emailChannelEnabledWithoutRegisteredSenderIsDiscarded() {
        when(settingsReader.getBoolean(SettingKey.NOTIFICATIONS_ENABLED)).thenReturn(true);
        when(settingsReader.getBoolean(SettingKey.NOTIFICATIONS_CHANNEL_EMAIL_ENABLED)).thenReturn(true);
        when(renderer.render(request.template(), request.model()))
                .thenReturn(new RenderedNotification("Aula confirmada", "<p>cuerpo</p>", List.of()));
        NotificationSenderImpl sender = new NotificationSenderImpl(
                settingsReader, new ChannelRegistry(List.of()), renderer, repository, eventPublisher, properties);

        NotificationResult result = sender.send(request);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.DISCARDED);
        assertThat(result).isEqualTo(new NotificationResult(0, 1));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void storesTheRequestReplyToOnTheQueuedNotification() {
        Notification saved = queueWith(List.of("aux1@frc.utn.edu.ar", "aux2@frc.utn.edu.ar"));

        assertThat(saved.getReplyTo()).isEqualTo("aux1@frc.utn.edu.ar,aux2@frc.utn.edu.ar");
    }

    @Test
    void addsTheConfiguredReplyToAfterTheRequestAddresses() {
        properties.getEmail().setReplyTo("secacad-gestionaulas@frc.utn.edu.ar");

        Notification saved = queueWith(List.of("aux1@frc.utn.edu.ar", "aux2@frc.utn.edu.ar"));

        assertThat(saved.getReplyTo())
                .isEqualTo("aux1@frc.utn.edu.ar,aux2@frc.utn.edu.ar,secacad-gestionaulas@frc.utn.edu.ar");
    }

    @Test
    void doesNotRepeatTheConfiguredReplyToWhenTheRequestAlreadyHasIt() {
        properties.getEmail().setReplyTo(" secacad-gestionaulas@frc.utn.edu.ar ");

        Notification saved = queueWith(List.of("Secacad-GestionAulas@frc.utn.edu.ar", "aux1@frc.utn.edu.ar"));

        assertThat(saved.getReplyTo()).isEqualTo("Secacad-GestionAulas@frc.utn.edu.ar,aux1@frc.utn.edu.ar");
    }

    @Test
    void usesTheConfiguredReplyToWhenTheRequestHasNone() {
        properties.getEmail().setReplyTo("subsecretaria@frc.utn.edu.ar");

        assertThat(queueWith(List.of()).getReplyTo()).isEqualTo("subsecretaria@frc.utn.edu.ar");
    }

    @Test
    void leavesReplyToEmptyWhenNeitherRequestNorConfigurationHaveOne() {
        properties.getEmail().setReplyTo("");

        assertThat(queueWith(List.of()).getReplyTo()).isNull();
    }

    @Test
    void storesCcAndBccJoinedByCommaOnTheQueuedNotification() {
        Notification saved = queueOne(copies(List.of("aux1@frc.utn.edu.ar", "aux2@frc.utn.edu.ar"),
                List.of("sub1@frc.utn.edu.ar", "sub2@frc.utn.edu.ar")));

        assertThat(saved.getCc()).isEqualTo("aux1@frc.utn.edu.ar,aux2@frc.utn.edu.ar");
        assertThat(saved.getBcc()).isEqualTo("sub1@frc.utn.edu.ar,sub2@frc.utn.edu.ar");
    }

    @Test
    void removesFromBccWhatIsAlreadyInCcRegardlessOfCase() {
        Notification saved = queueOne(copies(List.of("Subsecretaria@frc.utn.edu.ar"),
                List.of("subsecretaria@FRC.utn.edu.ar", "otra@frc.utn.edu.ar")));

        assertThat(saved.getCc()).isEqualTo("Subsecretaria@frc.utn.edu.ar");
        assertThat(saved.getBcc()).isEqualTo("otra@frc.utn.edu.ar");
    }

    @Test
    void leavesBccNullWhenEveryBccAddressIsAlreadyInCc() {
        Notification saved = queueOne(copies(List.of("sub@frc.utn.edu.ar"), List.of("SUB@frc.utn.edu.ar")));

        assertThat(saved.getCc()).isEqualTo("sub@frc.utn.edu.ar");
        assertThat(saved.getBcc()).isNull();
    }

    @Test
    void deduplicatesTrimsAndSkipsBlankOrNullCopyAddresses() {
        Notification saved = queueOne(copies(
                Arrays.asList(" aux@frc.utn.edu.ar ", "AUX@frc.utn.edu.ar", null, "  "),
                Arrays.asList("sub@frc.utn.edu.ar", " Sub@frc.utn.edu.ar", "", null)));

        assertThat(saved.getCc()).isEqualTo("aux@frc.utn.edu.ar");
        assertThat(saved.getBcc()).isEqualTo("sub@frc.utn.edu.ar");
    }

    @Test
    void leavesCcAndBccNullWhenTheListsAreNull() {
        Notification saved = queueOne(copies(null, null));

        assertThat(saved.getCc()).isNull();
        assertThat(saved.getBcc()).isNull();
    }

    @Test
    void leavesCcAndBccNullWhenTheListsAreEmpty() {
        Notification saved = queueOne(copies(List.of(), List.of()));

        assertThat(saved.getCc()).isNull();
        assertThat(saved.getBcc()).isNull();
    }

    @Test
    void doesNotMixTheReplyToIntoTheCopies() {
        properties.getEmail().setReplyTo("secacad-gestionaulas@frc.utn.edu.ar");

        Notification saved = queueOne(new NotificationRequest(request.template(), request.recipients(),
                request.model(), request.idempotencyKey(), List.of("aux@frc.utn.edu.ar"),
                List.of("cc@frc.utn.edu.ar"), List.of("cco@frc.utn.edu.ar")));

        assertThat(saved.getReplyTo()).isEqualTo("aux@frc.utn.edu.ar,secacad-gestionaulas@frc.utn.edu.ar");
        assertThat(saved.getCc()).isEqualTo("cc@frc.utn.edu.ar");
        assertThat(saved.getBcc()).isEqualTo("cco@frc.utn.edu.ar");
    }

    @Test
    void savesOneRowPerRecipientWithTheSameCopies() {
        NotificationRequest twoRecipients = new NotificationRequest(request.template(),
                List.of(new NotificationRecipient("Ana", "ana@frc.utn.edu.ar"),
                        new NotificationRecipient("Beto", "beto@frc.utn.edu.ar")),
                request.model(), request.idempotencyKey(), List.of(),
                List.of("aux@frc.utn.edu.ar"), List.of("sub@frc.utn.edu.ar"));

        List<Notification> saved = queue(twoRecipients);

        assertThat(saved).extracting(Notification::getRecipient)
                .containsExactly("ana@frc.utn.edu.ar", "beto@frc.utn.edu.ar");
        assertThat(saved).extracting(Notification::getCc).containsOnly("aux@frc.utn.edu.ar");
        assertThat(saved).extracting(Notification::getBcc).containsOnly("sub@frc.utn.edu.ar");
    }

    @Test
    void theFourAndFiveArgumentConstructorsLeaveTheCopiesEmptyAndSaveNullColumns() {
        NotificationRequest fourArgs = new NotificationRequest(request.template(), request.recipients(),
                request.model(), request.idempotencyKey());
        NotificationRequest fiveArgs = new NotificationRequest(request.template(), request.recipients(),
                request.model(), request.idempotencyKey(), List.of("aux@frc.utn.edu.ar"));

        assertThat(fourArgs.cc()).isEmpty();
        assertThat(fourArgs.bcc()).isEmpty();
        assertThat(fiveArgs.replyTo()).containsExactly("aux@frc.utn.edu.ar");
        assertThat(fiveArgs.cc()).isEmpty();
        assertThat(fiveArgs.bcc()).isEmpty();
        Notification savedFromFive = queueOne(fiveArgs);
        assertThat(savedFromFive.getCc()).isNull();
        assertThat(savedFromFive.getBcc()).isNull();
        assertThat(savedFromFive.getReplyTo()).isEqualTo("aux@frc.utn.edu.ar");
    }

    @Test
    void truncatesASubjectOf300CharactersTo255KeepingThePrefix() {
        String subject = "A".repeat(100) + "B".repeat(100) + "C".repeat(100);

        Notification saved = queueWithSubject(subject);

        assertThat(saved.getSubject()).hasSize(255).isEqualTo(subject.substring(0, 255));
    }

    @Test
    void truncatesASubjectOf256CharactersTo255() {
        assertThat(queueWithSubject("x".repeat(256)).getSubject()).hasSize(255);
    }

    @Test
    void keepsASubjectOfExactly255CharactersIntact() {
        String subject = "y".repeat(255);

        assertThat(queueWithSubject(subject).getSubject()).isEqualTo(subject);
    }

    @Test
    void keepsASubjectOf254CharactersIntact() {
        String subject = "z".repeat(254);

        assertThat(queueWithSubject(subject).getSubject()).isEqualTo(subject);
    }

    @Test
    void keepsAShortSubjectIntact() {
        assertThat(queueWithSubject("Aula confirmada").getSubject()).isEqualTo("Aula confirmada");
    }

    private Notification queueWithSubject(String subject) {
        when(settingsReader.getBoolean(SettingKey.NOTIFICATIONS_ENABLED)).thenReturn(true);
        when(settingsReader.getBoolean(SettingKey.NOTIFICATIONS_CHANNEL_EMAIL_ENABLED)).thenReturn(true);
        when(renderer.render(any(), any())).thenReturn(new RenderedNotification(subject, "<p>cuerpo</p>", List.of()));
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        ChannelSender email = org.mockito.Mockito.mock(ChannelSender.class);
        when(email.channel()).thenReturn(NotificationChannel.EMAIL);
        NotificationSenderImpl sender = new NotificationSenderImpl(
                settingsReader, new ChannelRegistry(List.of(email)), renderer, repository, eventPublisher, properties);

        sender.send(request);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private NotificationRequest copies(List<String> cc, List<String> bcc) {
        return new NotificationRequest(request.template(), request.recipients(), request.model(),
                request.idempotencyKey(), List.of(), cc, bcc);
    }

    private Notification queueOne(NotificationRequest toSend) {
        List<Notification> saved = queue(toSend);
        assertThat(saved).hasSize(1);
        return saved.getFirst();
    }

    private List<Notification> queue(NotificationRequest toSend) {
        when(settingsReader.getBoolean(SettingKey.NOTIFICATIONS_ENABLED)).thenReturn(true);
        when(settingsReader.getBoolean(SettingKey.NOTIFICATIONS_CHANNEL_EMAIL_ENABLED)).thenReturn(true);
        when(renderer.render(any(), any())).thenReturn(new RenderedNotification("Aula asignada", "<p>cuerpo</p>", List.of()));
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        ChannelSender email = org.mockito.Mockito.mock(ChannelSender.class);
        when(email.channel()).thenReturn(NotificationChannel.EMAIL);
        NotificationSenderImpl sender = new NotificationSenderImpl(
                settingsReader, new ChannelRegistry(List.of(email)), renderer, repository, eventPublisher, properties);

        sender.send(toSend);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private Notification queueWith(List<String> replyTo) {
        when(settingsReader.getBoolean(SettingKey.NOTIFICATIONS_ENABLED)).thenReturn(true);
        when(settingsReader.getBoolean(SettingKey.NOTIFICATIONS_CHANNEL_EMAIL_ENABLED)).thenReturn(true);
        when(renderer.render(any(), any())).thenReturn(new RenderedNotification("Aula asignada", "<p>cuerpo</p>", List.of()));
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        ChannelSender email = org.mockito.Mockito.mock(ChannelSender.class);
        when(email.channel()).thenReturn(NotificationChannel.EMAIL);
        NotificationSenderImpl sender = new NotificationSenderImpl(
                settingsReader, new ChannelRegistry(List.of(email)), renderer, repository, eventPublisher, properties);

        sender.send(new NotificationRequest(request.template(), request.recipients(), request.model(),
                request.idempotencyKey(), replyTo));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
