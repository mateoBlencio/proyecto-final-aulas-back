package ar.edu.utn.frc.siga.notification.internal.service;

import ar.edu.utn.frc.siga.notification.api.NotificationChannel;
import ar.edu.utn.frc.siga.notification.internal.channel.ChannelRegistry;
import ar.edu.utn.frc.siga.notification.internal.channel.ChannelSender;
import ar.edu.utn.frc.siga.notification.internal.config.NotificationProperties;
import ar.edu.utn.frc.siga.notification.internal.model.Notification;
import ar.edu.utn.frc.siga.notification.internal.model.NotificationStatus;
import ar.edu.utn.frc.siga.notification.internal.render.RenderedNotification;
import ar.edu.utn.frc.siga.notification.internal.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.assertj.core.data.TemporalUnitWithinOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    private NotificationRepository repository;
    @Mock
    private ChannelSender channelSender;

    @Test
    void firstFailureLeavesNotificationPendingWithOneMinuteBackoff() {
        NotificationDispatcher dispatcher = newDispatcher();
        Notification notification = pendingNotification(0);
        when(repository.findById(1L)).thenReturn(Optional.of(notification));
        doThrow(new IllegalStateException("SMTP caído")).when(channelSender).send(any(), any());

        dispatcher.dispatch(1L);

        assertThat(notification.getAttempts()).isEqualTo(1);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getNextAttemptAt())
                .isCloseTo(Instant.now().plus(Duration.ofMinutes(1)), within(5));
    }

    @Test
    void fifthFailureMarksNotificationFailedWithNoNextAttempt() {
        NotificationDispatcher dispatcher = newDispatcher();
        Notification notification = pendingNotification(4);
        when(repository.findById(1L)).thenReturn(Optional.of(notification));
        doThrow(new IllegalStateException("SMTP caído")).when(channelSender).send(any(), any());

        dispatcher.dispatch(1L);

        assertThat(notification.getAttempts()).isEqualTo(5);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getNextAttemptAt()).isNull();
    }

    @Test
    void retryPassesTheStoredReplyToAddressesToTheChannel() {
        NotificationDispatcher dispatcher = newDispatcher();
        Notification notification = Notification.builder()
                .id(1L).template("ROOM_REQUEST_RESOLVED").channel(NotificationChannel.EMAIL)
                .recipient("docente@frc.utn.edu.ar").subject("Aula asignada").body("<p>cuerpo</p>")
                .status(NotificationStatus.PENDING).idempotencyKey("k")
                .replyTo("aux1@frc.utn.edu.ar,aux2@frc.utn.edu.ar").build();
        when(repository.findById(1L)).thenReturn(Optional.of(notification));

        dispatcher.dispatch(1L);

        org.mockito.ArgumentCaptor<ar.edu.utn.frc.siga.notification.internal.render.RenderedNotification> captor =
                org.mockito.ArgumentCaptor.forClass(ar.edu.utn.frc.siga.notification.internal.render.RenderedNotification.class);
        org.mockito.Mockito.verify(channelSender).send(captor.capture(), any());
        assertThat(captor.getValue().replyTo()).containsExactly("aux1@frc.utn.edu.ar", "aux2@frc.utn.edu.ar");
    }

    @Test
    void dispatchPassesTheStoredCcAndBccAddressesToTheChannel() {
        NotificationDispatcher dispatcher = newDispatcher();
        Notification notification = withCopies("aux@frc.utn.edu.ar", "sub1@frc.utn.edu.ar,sub2@frc.utn.edu.ar");
        when(repository.findById(1L)).thenReturn(Optional.of(notification));

        dispatcher.dispatch(1L);

        RenderedNotification sent = sentMessages(1).getFirst();
        assertThat(sent.cc()).containsExactly("aux@frc.utn.edu.ar");
        assertThat(sent.bcc()).containsExactly("sub1@frc.utn.edu.ar", "sub2@frc.utn.edu.ar");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void aRetryAfterAFailureKeepsTheCopies() {
        NotificationDispatcher dispatcher = newDispatcher();
        Notification notification = withCopies("aux@frc.utn.edu.ar", "sub@frc.utn.edu.ar");
        when(repository.findById(1L)).thenReturn(Optional.of(notification));
        doThrow(new IllegalStateException("SMTP caído")).doNothing().when(channelSender).send(any(), any());

        dispatcher.dispatch(1L);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        dispatcher.dispatch(1L);

        List<RenderedNotification> sent = sentMessages(2);
        assertThat(sent).extracting(RenderedNotification::cc).containsOnly(List.of("aux@frc.utn.edu.ar"));
        assertThat(sent).extracting(RenderedNotification::bcc).containsOnly(List.of("sub@frc.utn.edu.ar"));
        assertThat(notification.getAttempts()).isEqualTo(1);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void aNotificationWithoutCopiesProducesEmptyCcAndBcc() {
        NotificationDispatcher dispatcher = newDispatcher();
        Notification notification = pendingNotification(0);
        when(repository.findById(1L)).thenReturn(Optional.of(notification));

        dispatcher.dispatch(1L);

        RenderedNotification sent = sentMessages(1).getFirst();
        assertThat(sent.cc()).isEmpty();
        assertThat(sent.bcc()).isEmpty();
    }

    @Test
    void backoffForThirdAttemptIsFourMinutes() {
        assertThat(NotificationDispatcher.backoffFor(3)).isEqualTo(Duration.ofMinutes(4));
    }

    @Test
    void backoffForTenthAttemptIsCappedAtOneHour() {
        assertThat(NotificationDispatcher.backoffFor(10)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void expiredSensitiveNotificationIsDiscardedWithoutCallingTheChannel() {
        NotificationDispatcher dispatcher = newDispatcher();
        Notification notification = sensitive("PASSWORD_RESET", Instant.now().minus(Duration.ofMinutes(31)));
        when(repository.findById(1L)).thenReturn(Optional.of(notification));

        dispatcher.dispatch(1L);

        verify(channelSender, never()).send(any(), any());
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.DISCARDED);
        assertThat(notification.getBody()).isEqualTo("[contenido omitido]");
    }

    @Test
    void unexpiredSensitiveNotificationIsSentAndItsBodyRedacted() {
        NotificationDispatcher dispatcher = newDispatcher();
        Notification notification = sensitive("PASSWORD_RESET", Instant.now().minus(Duration.ofMinutes(5)));
        when(repository.findById(1L)).thenReturn(Optional.of(notification));

        dispatcher.dispatch(1L);

        assertThat(sentMessages(1).getFirst().body()).isEqualTo("<p>enlace secreto</p>");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getBody()).isEqualTo("[contenido omitido]");
    }

    @Test
    void oldNonSensitiveNotificationIsStillSent() {
        NotificationDispatcher dispatcher = newDispatcher();
        Notification notification = sensitive("ROOM_REQUEST_RESOLVED", Instant.now().minus(Duration.ofDays(2)));
        when(repository.findById(1L)).thenReturn(Optional.of(notification));

        dispatcher.dispatch(1L);

        sentMessages(1);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getBody()).isEqualTo("<p>enlace secreto</p>");
    }

    private static Notification sensitive(String template, Instant createdAt) {
        Notification notification = Notification.builder()
                .id(1L).template(template).channel(NotificationChannel.EMAIL)
                .recipient("docente@frc.utn.edu.ar").recipientName("Docente").subject("Asunto")
                .body("<p>enlace secreto</p>").status(NotificationStatus.PENDING).idempotencyKey("k").build();
        ReflectionTestUtils.setField(notification, "createdAt", createdAt);
        return notification;
    }

    private NotificationDispatcher newDispatcher() {
        when(channelSender.channel()).thenReturn(NotificationChannel.EMAIL);
        return new NotificationDispatcher(repository, new ChannelRegistry(List.of(channelSender)), new NotificationProperties());
    }

    private List<RenderedNotification> sentMessages(int times) {
        ArgumentCaptor<RenderedNotification> captor = ArgumentCaptor.forClass(RenderedNotification.class);
        verify(channelSender, times(times)).send(captor.capture(), any());
        return captor.getAllValues();
    }

    private static Notification withCopies(String cc, String bcc) {
        return Notification.builder()
                .id(1L).template("ROOM_REQUEST_RESOLVED").channel(NotificationChannel.EMAIL)
                .recipient("docente@frc.utn.edu.ar").recipientName("Docente").subject("Propuesta de aula")
                .body("<p>cuerpo</p>").status(NotificationStatus.PENDING).idempotencyKey("k")
                .cc(cc).bcc(bcc).build();
    }

    private static Notification pendingNotification(int attempts) {
        return Notification.builder()
                .id(1L)
                .template("ROOM_REQUEST_RESOLVED")
                .channel(NotificationChannel.EMAIL)
                .recipient("docente@frc.utn.edu.ar")
                .recipientName("Docente")
                .subject("Aula confirmada")
                .body("<p>cuerpo</p>")
                .status(NotificationStatus.PENDING)
                .attempts(attempts)
                .idempotencyKey("room-request-item:1:RESOLVED")
                .build();
    }

    private static TemporalUnitWithinOffset within(long seconds) {
        return new TemporalUnitWithinOffset(seconds, ChronoUnit.SECONDS);
    }
}
