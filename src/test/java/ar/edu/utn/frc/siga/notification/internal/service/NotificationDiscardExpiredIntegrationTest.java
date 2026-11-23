package ar.edu.utn.frc.siga.notification.internal.service;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.notification.api.NotificationChannel;
import ar.edu.utn.frc.siga.notification.internal.model.Notification;
import ar.edu.utn.frc.siga.notification.internal.model.NotificationStatus;
import ar.edu.utn.frc.siga.notification.internal.repository.NotificationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Descarte de notificaciones sensibles vencidas (integración)")
class NotificationDiscardExpiredIntegrationTest extends AbstractIntegrationTest {

    private static final String BODY = "<a href=\"https://siga/restablecer#token=secreto\">enlace</a>";
    private static final String REDACTED = "[contenido omitido]";

    @Autowired
    private NotificationRepository repository;
    @Autowired
    private NotificationRetryClaimer claimer;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("discardExpiredPending descarta la PASSWORD_RESET de hace 31 minutos y deja intactas la de 5 minutos, la ya enviada y otra plantilla")
    void discardsOnlyTheExpiredPendingRowOfTheTemplate() {
        Long expired = saveAged("PASSWORD_RESET", NotificationStatus.PENDING, 31);
        Long fresh = saveAged("PASSWORD_RESET", NotificationStatus.PENDING, 5);
        Long oldSent = saveAged("PASSWORD_RESET", NotificationStatus.SENT, 120);
        Long oldOtherTemplate = saveAged("ROOM_REQUEST_RESOLVED", NotificationStatus.PENDING, 60 * 24);

        int updated = transactionTemplate.execute(status ->
                repository.discardExpiredPending("PASSWORD_RESET", Instant.now().minus(Duration.ofMinutes(30))));

        assertThat(updated).isGreaterThanOrEqualTo(1);
        assertThat(row(expired)).containsEntry("estado", "DISCARDED").containsEntry("cuerpo", REDACTED)
                .containsEntry("ultimo_error", "vencida antes de enviarse");
        assertThat(row(fresh)).containsEntry("estado", "PENDING").containsEntry("cuerpo", BODY);
        assertThat(row(oldSent)).containsEntry("estado", "SENT").containsEntry("cuerpo", BODY);
        assertThat(row(oldOtherTemplate)).containsEntry("estado", "PENDING").containsEntry("cuerpo", BODY);
    }

    @Test
    @DisplayName("el claimer calcula el corte con el vencimiento de la plantilla: 31 minutos se descarta, 29 no")
    void claimerUsesTheTemplateExpiry() {
        Long expired = saveAged("PASSWORD_RESET", NotificationStatus.PENDING, 31);
        Long fresh = saveAged("PASSWORD_RESET", NotificationStatus.PENDING, 29);
        Long oldOtherTemplate = saveAged("ROOM_REQUEST_RESOLVED", NotificationStatus.PENDING, 60 * 24);

        claimer.discardExpiredSensitive();

        assertThat(row(expired)).containsEntry("estado", "DISCARDED").containsEntry("cuerpo", REDACTED);
        assertThat(row(fresh)).containsEntry("estado", "PENDING").containsEntry("cuerpo", BODY);
        assertThat(row(oldOtherTemplate)).containsEntry("estado", "PENDING").containsEntry("cuerpo", BODY);
    }

    private Long saveAged(String template, NotificationStatus status, int minutesOld) {
        Long id = repository.saveAndFlush(Notification.builder()
                .template(template)
                .channel(NotificationChannel.EMAIL)
                .recipient("docente.discard." + System.nanoTime() + "@frc.utn.edu.ar")
                .recipientName("Docente")
                .subject("Asunto")
                .body(BODY)
                .status(status)
                .idempotencyKey("discard-expired:" + System.nanoTime())
                .build()).getId();
        jdbcTemplate.update("UPDATE notificacion SET creado_en = creado_en - make_interval(mins => ?) "
                + "WHERE id_notificacion = ?", minutesOld, id);
        return id;
    }

    private Map<String, Object> row(Long id) {
        return jdbcTemplate.queryForMap(
                "SELECT estado, cuerpo, ultimo_error FROM notificacion WHERE id_notificacion = ?", id);
    }
}
