package ar.edu.utn.frc.siga.notification.internal.repository;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.notification.api.NotificationChannel;
import ar.edu.utn.frc.siga.notification.internal.model.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("findDueForRetry contra zona horaria de la JVM (integración)")
class NotificationRepositoryDueForRetryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationRepository repository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("la JVM de test corre en America/Argentina/Buenos_Aires")
    void jvmRunsInBuenosAires() {
        assertThat(TimeZone.getDefault().getID()).isEqualTo("America/Argentina/Buenos_Aires");
    }

    @Test
    @DisplayName("filas vencidas hace 1 hora y 5 minutos aparecen; las de dentro de 5 minutos y 1 hora no")
    void returnsOnlyRowsWhoseNextAttemptHasPassed() {
        Instant now = Instant.now();
        Long overdueHour = save(now.minus(Duration.ofHours(1)));
        Long overdueMinutes = save(now.minus(Duration.ofMinutes(5)));
        Long upcomingMinutes = save(now.plus(Duration.ofMinutes(5)));
        Long upcomingHour = save(now.plus(Duration.ofHours(1)));

        List<Long> due = transactionTemplate.execute(status -> repository.findDueForRetry(1000));

        assertThat(due).contains(overdueHour, overdueMinutes);
        assertThat(due).doesNotContain(upcomingMinutes, upcomingHour);
    }

    private Long save(Instant nextAttemptAt) {
        return repository.save(Notification.builder()
                .template("ROOM_REQUEST_RESOLVED")
                .channel(NotificationChannel.EMAIL)
                .recipient("docente.due." + System.nanoTime() + "@frc.utn.edu.ar")
                .recipientName("Docente")
                .subject("Aula confirmada")
                .body("<p>cuerpo</p>")
                .idempotencyKey("due-retry:" + System.nanoTime())
                .nextAttemptAt(nextAttemptAt)
                .build()).getId();
    }
}
