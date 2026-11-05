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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    void usesTheConfiguredReplyToWhenTheRequestHasNone() {
        properties.getEmail().setReplyTo("subsecretaria@frc.utn.edu.ar");

        assertThat(queueWith(List.of()).getReplyTo()).isEqualTo("subsecretaria@frc.utn.edu.ar");
    }

    @Test
    void leavesReplyToEmptyWhenNeitherRequestNorConfigurationHaveOne() {
        properties.getEmail().setReplyTo("");

        assertThat(queueWith(List.of()).getReplyTo()).isNull();
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
