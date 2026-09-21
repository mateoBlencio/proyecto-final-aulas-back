package ar.edu.utn.frc.siga.notification.internal.service;

import ar.edu.utn.frc.siga.notification.api.NotificationChannel;
import ar.edu.utn.frc.siga.notification.internal.channel.ChannelRegistry;
import ar.edu.utn.frc.siga.notification.internal.channel.ChannelSender;
import ar.edu.utn.frc.siga.notification.internal.config.NotificationProperties;
import ar.edu.utn.frc.siga.notification.internal.model.Notification;
import ar.edu.utn.frc.siga.notification.internal.model.NotificationStatus;
import ar.edu.utn.frc.siga.notification.internal.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.assertj.core.data.TemporalUnitWithinOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
    void backoffForThirdAttemptIsFourMinutes() {
        assertThat(NotificationDispatcher.backoffFor(3)).isEqualTo(Duration.ofMinutes(4));
    }

    @Test
    void backoffForTenthAttemptIsCappedAtOneHour() {
        assertThat(NotificationDispatcher.backoffFor(10)).isEqualTo(Duration.ofHours(1));
    }

    private NotificationDispatcher newDispatcher() {
        when(channelSender.channel()).thenReturn(NotificationChannel.EMAIL);
        return new NotificationDispatcher(repository, new ChannelRegistry(List.of(channelSender)), new NotificationProperties());
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
