package ar.edu.utn.frc.siga.notification;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.notification.api.NotificationChannel;
import ar.edu.utn.frc.siga.notification.api.NotificationRecipient;
import ar.edu.utn.frc.siga.notification.api.NotificationRequest;
import ar.edu.utn.frc.siga.notification.api.NotificationResult;
import ar.edu.utn.frc.siga.notification.api.NotificationSender;
import ar.edu.utn.frc.siga.notification.api.NotificationTemplate;
import ar.edu.utn.frc.siga.notification.internal.channel.ChannelSender;
import ar.edu.utn.frc.siga.notification.internal.render.RenderedNotification;
import ar.edu.utn.frc.siga.notification.internal.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Import(NotificationOutboxIntegrationTest.FakeChannelSenderConfig.class)
@DisplayName("Notification outbox (integración)")
class NotificationOutboxIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationSender notificationSender;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private FakeChannelSender fakeChannelSender;

    @Test
    @DisplayName("dos envíos con la misma clave de idempotencia dejan una sola fila y un solo envío")
    void duplicateIdempotencyKeyIsQueuedOnceAndSentOnce() {
        String idempotencyKey = "room-request-item:" + System.nanoTime() + ":RESOLVED";
        NotificationRequest request = new NotificationRequest(
                NotificationTemplate.ROOM_REQUEST_RESOLVED,
                List.of(new NotificationRecipient("Docente de prueba", "docente.integracion@frc.utn.edu.ar")),
                Map.of("docente", "Docente de prueba", "pedidoId", "#1", "tipoTexto", "Cambio de aula — regular"),
                idempotencyKey);

        NotificationResult first = notificationSender.send(request);
        NotificationResult second = notificationSender.send(request);

        assertThat(first.queued()).isEqualTo(1);
        assertThat(second.queued()).isEqualTo(0);
        assertThat(notificationRepository.countByIdempotencyKey(idempotencyKey)).isEqualTo(1);
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(fakeChannelSender.sent()).hasSize(1));
    }

    static class FakeChannelSender implements ChannelSender {

        private final List<RenderedNotification> sent = new CopyOnWriteArrayList<>();

        @Override
        public NotificationChannel channel() {
            return NotificationChannel.EMAIL;
        }

        @Override
        public void send(RenderedNotification message, NotificationRecipient recipient) {
            sent.add(message);
        }

        List<RenderedNotification> sent() {
            return sent;
        }
    }

    @TestConfiguration
    static class FakeChannelSenderConfig {

        @Bean
        FakeChannelSender fakeChannelSender() {
            return new FakeChannelSender();
        }
    }
}
