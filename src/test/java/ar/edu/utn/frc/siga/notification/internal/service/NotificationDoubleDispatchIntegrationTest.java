package ar.edu.utn.frc.siga.notification.internal.service;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.notification.api.NotificationChannel;
import ar.edu.utn.frc.siga.notification.api.NotificationRecipient;
import ar.edu.utn.frc.siga.notification.api.NotificationRequest;
import ar.edu.utn.frc.siga.notification.api.NotificationSender;
import ar.edu.utn.frc.siga.notification.api.NotificationTemplate;
import ar.edu.utn.frc.siga.notification.internal.channel.ChannelSender;
import ar.edu.utn.frc.siga.notification.internal.model.Notification;
import ar.edu.utn.frc.siga.notification.internal.model.NotificationStatus;
import ar.edu.utn.frc.siga.notification.internal.render.RenderedNotification;
import ar.edu.utn.frc.siga.notification.internal.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Import(NotificationDoubleDispatchIntegrationTest.FakeChannelSenderConfig.class)
@TestPropertySource(properties = "siga.notifications.retry-interval=PT1H")
@DisplayName("DT-005: el scheduler no despacha una fila que el listener ya reclamó (integración)")
class NotificationDoubleDispatchIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationSender notificationSender;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private NotificationRetryScheduler notificationRetryScheduler;
    @Autowired
    private FakeChannelSender fakeChannelSender;

    @Test
    @DisplayName("scheduler corriendo mientras el listener despacha no duplica el envío")
    void schedulerRunningWhileListenerDispatchesDoesNotDuplicateTheSend() throws InterruptedException {
        String recipientEmail = "docente.dt005." + System.nanoTime() + "@frc.utn.edu.ar";
        String idempotencyKey = "room-request-item:" + System.nanoTime() + ":RESOLVED";
        NotificationRequest request = new NotificationRequest(
                NotificationTemplate.ROOM_REQUEST_RESOLVED,
                List.of(new NotificationRecipient("Docente de prueba", recipientEmail)),
                Map.of("docente", "Docente de prueba", "pedidoId", "#1", "tipoTexto", "Cambio de aula — regular"),
                idempotencyKey);

        notificationSender.send(request);

        assertThat(fakeChannelSender.entered.await(5, TimeUnit.SECONDS))
                .as("el listener async debería haber entrado al envío")
                .isTrue();

        notificationRetryScheduler.run();
        fakeChannelSender.release.countDown();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Notification notification = notificationByIdempotencyKey(idempotencyKey);
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        });
        assertThat(fakeChannelSender.sentTo(recipientEmail)).hasSize(1);
    }

    private Notification notificationByIdempotencyKey(String idempotencyKey) {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getIdempotencyKey().equals(idempotencyKey))
                .findFirst()
                .orElseThrow();
    }

    static class FakeChannelSender implements ChannelSender {

        private final AtomicBoolean firstCall = new AtomicBoolean(true);
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final List<String> sent = new CopyOnWriteArrayList<>();

        @Override
        public NotificationChannel channel() {
            return NotificationChannel.EMAIL;
        }

        @Override
        public void send(RenderedNotification message, NotificationRecipient recipient) {
            if (firstCall.compareAndSet(true, false)) {
                entered.countDown();
                awaitRelease();
            }
            sent.add(recipient.email());
        }

        private void awaitRelease() {
            try {
                boolean released = release.await(5, TimeUnit.SECONDS);
                if (!released) {
                    throw new IllegalStateException("el test no liberó el envío bloqueado a tiempo");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }

        List<String> sentTo(String recipientEmail) {
            return sent.stream().filter(recipientEmail::equals).toList();
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
