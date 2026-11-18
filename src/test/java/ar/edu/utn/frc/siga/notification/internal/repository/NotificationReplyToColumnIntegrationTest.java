package ar.edu.utn.frc.siga.notification.internal.repository;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.notification.api.NotificationChannel;
import ar.edu.utn.frc.siga.notification.internal.model.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("responder_a guarda listas largas de Reply-To (V64, integración)")
class NotificationReplyToColumnIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationRepository repository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("un Reply-To de unos 1500 caracteres se guarda y se relee completo")
    void persistsAndReadsBackAReplyToLongerThanTheOldLimit() {
        String replyTo = IntStream.rangeClosed(1, 44)
                .mapToObj(i -> "auxiliar-aulico-%02d@frc.utn.edu.ar".formatted(i))
                .collect(Collectors.joining(","));
        assertThat(replyTo.length()).isBetween(1400, 1600);

        Long id = repository.save(Notification.builder()
                .template("ROOM_REQUEST_RESOLVED")
                .channel(NotificationChannel.EMAIL)
                .recipient("docente.replyto." + System.nanoTime() + "@frc.utn.edu.ar")
                .recipientName("Docente")
                .subject("Propuesta de aula")
                .body("<p>cuerpo</p>")
                .replyTo(replyTo)
                .idempotencyKey("reply-to-column:" + System.nanoTime())
                .build()).getId();

        assertThat(repository.findById(id)).get().extracting(Notification::getReplyTo).isEqualTo(replyTo);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT responder_a FROM notificacion WHERE id_notificacion = ?", String.class, id))
                .isEqualTo(replyTo);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_name = 'notificacion' AND column_name = 'responder_a'", String.class))
                .isEqualTo("text");
    }
}
