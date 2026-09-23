package ar.edu.utn.frc.siga.notification.internal.service;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.notification.api.NotificationChannel;
import ar.edu.utn.frc.siga.notification.internal.model.Notification;
import ar.edu.utn.frc.siga.notification.internal.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "siga.notifications.retry-interval=PT1H")
@DisplayName("Claim unificado antes de despachar (integración)")
class NotificationClaimIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationRetryClaimer claimer;
    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    @DisplayName("claim reclama la fila y claimDue deja de verla hasta que vence el lease")
    void claimReclaimsRowAndClaimDueStopsSeeingIt() {
        Long id = savePending(Instant.now()).getId();

        List<Long> claimed = claimer.claim(List.of(id));

        assertThat(claimed).containsExactly(id);
        assertThat(claimer.claimDue(50)).doesNotContain(id);
        assertThat(claimer.claim(List.of(id))).isEmpty();
    }

    @Test
    @DisplayName("claimDue reclama la fila y claim deja de verla")
    void claimDueReclaimsRowAndClaimStopsSeeingIt() {
        Long id = savePending(Instant.now()).getId();

        List<Long> claimed = claimer.claimDue(50);

        assertThat(claimed).contains(id);
        assertThat(claimer.claim(List.of(id))).isEmpty();
    }

    @Test
    @DisplayName("claim ignora una fila con próximo intento en el futuro")
    void claimIgnoresRowScheduledInTheFuture() {
        Long id = savePending(Instant.now().plus(Duration.ofHours(1))).getId();

        assertThat(claimer.claim(List.of(id))).isEmpty();
    }

    @Test
    @DisplayName("claim ignora una fila que ya fue marcada como enviada")
    void claimIgnoresRowAlreadySent() {
        Notification notification = savePending(Instant.now());
        notification.markSent(Instant.now());
        notificationRepository.save(notification);

        assertThat(claimer.claim(List.of(notification.getId()))).isEmpty();
    }

    private Notification savePending(Instant nextAttemptAt) {
        return notificationRepository.save(Notification.builder()
                .template("ROOM_REQUEST_RESOLVED")
                .channel(NotificationChannel.EMAIL)
                .recipient("docente.claim." + System.nanoTime() + "@frc.utn.edu.ar")
                .recipientName("Docente")
                .subject("Aula confirmada")
                .body("<p>cuerpo</p>")
                .idempotencyKey("room-request-item:" + System.nanoTime() + ":RESOLVED")
                .nextAttemptAt(nextAttemptAt)
                .build());
    }
}
