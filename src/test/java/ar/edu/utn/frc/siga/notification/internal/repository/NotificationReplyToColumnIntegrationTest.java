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

@DisplayName("columnas responder_a (V64), cc y cco (V75) de notificacion (integración)")
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

    @Test
    @DisplayName("cc y cco se guardan y se releen completos, ambas columnas son text (V75)")
    void persistsAndReadsBackCcAndCco() {
        String cc = "auxiliar@frc.utn.edu.ar";
        String cco = IntStream.rangeClosed(1, 44)
                .mapToObj(i -> "subsecretaria-%02d@frc.utn.edu.ar".formatted(i))
                .collect(Collectors.joining(","));

        Long id = repository.save(notification("copias", cc, cco)).getId();

        assertThat(repository.findById(id)).get()
                .satisfies(saved -> {
                    assertThat(saved.getCc()).isEqualTo(cc);
                    assertThat(saved.getBcc()).isEqualTo(cco);
                });
        assertThat(jdbcTemplate.queryForMap("SELECT cc, cco FROM notificacion WHERE id_notificacion = ?", id))
                .containsEntry("cc", cc)
                .containsEntry("cco", cco);
        assertThat(jdbcTemplate.queryForList(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_name = 'notificacion' AND column_name IN ('cc', 'cco')", String.class))
                .containsExactly("text", "text");
    }

    @Test
    @DisplayName("una fila sin copias deja cc y cco en null")
    void aRowWithoutCopiesLeavesBothColumnsNull() {
        Long id = repository.save(notification("sin-copias", null, null)).getId();

        assertThat(repository.findById(id)).get().satisfies(saved -> {
            assertThat(saved.getCc()).isNull();
            assertThat(saved.getBcc()).isNull();
        });
        assertThat(jdbcTemplate.queryForMap("SELECT cc, cco FROM notificacion WHERE id_notificacion = ?", id))
                .containsEntry("cc", null)
                .containsEntry("cco", null);
    }

    private static Notification notification(String keyPrefix, String cc, String cco) {
        return Notification.builder()
                .template("ROOM_REQUEST_RESOLVED")
                .channel(NotificationChannel.EMAIL)
                .recipient("docente.copias." + System.nanoTime() + "@frc.utn.edu.ar")
                .recipientName("Docente")
                .subject("Propuesta de aula")
                .body("<p>cuerpo</p>")
                .cc(cc)
                .bcc(cco)
                .idempotencyKey(keyPrefix + ":" + System.nanoTime())
                .build();
    }
}
