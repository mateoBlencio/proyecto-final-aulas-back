package ar.edu.utn.frc.siga.notification.internal.model;

import ar.edu.utn.frc.siga.notification.api.NotificationChannel;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("Notification")
class NotificationTest {

    private static final String BODY = "<a href=\"https://siga/restablecer#token=secreto\">enlace</a>";
    private static final String REDACTED = "[contenido omitido]";

    @Test
    @DisplayName("PASSWORD_RESET borra el cuerpo al marcarse enviada")
    void sensitiveBodyIsRedactedOnSent() {
        Notification notification = notification("PASSWORD_RESET");

        notification.markSent(Instant.now());

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getBody()).isEqualTo(REDACTED);
    }

    @Test
    @DisplayName("PASSWORD_RESET borra el cuerpo al descartarse")
    void sensitiveBodyIsRedactedOnDiscarded() {
        Notification notification = notification("PASSWORD_RESET");

        notification.markDiscarded("vencida antes de enviarse");

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.DISCARDED);
        assertThat(notification.getBody()).isEqualTo(REDACTED);
    }

    @Test
    @DisplayName("PASSWORD_RESET borra el cuerpo al llegar a FAILED")
    void sensitiveBodyIsRedactedOnFinalFailure() {
        Notification notification = notification("PASSWORD_RESET");

        notification.registerFailure("SMTP caído", Duration.ofMinutes(1), 1);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getBody()).isEqualTo(REDACTED);
    }

    @Test
    @DisplayName("PASSWORD_RESET conserva el cuerpo en un fallo intermedio para poder reintentar")
    void sensitiveBodyIsKeptOnIntermediateFailure() {
        Notification notification = notification("PASSWORD_RESET");

        notification.registerFailure("SMTP caído", Duration.ofMinutes(1), 5);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getBody()).isEqualTo(BODY);
    }

    @Test
    @DisplayName("ROOM_REQUEST_RESOLVED conserva el cuerpo en envío, descarte y fallo final")
    void nonSensitiveBodyIsAlwaysKept() {
        Notification sent = notification("ROOM_REQUEST_RESOLVED");
        sent.markSent(Instant.now());
        Notification discarded = notification("ROOM_REQUEST_RESOLVED");
        discarded.markDiscarded("motivo");
        Notification failed = notification("ROOM_REQUEST_RESOLVED");
        failed.registerFailure("error", Duration.ofMinutes(1), 1);

        assertThat(sent.getBody()).isEqualTo(BODY);
        assertThat(discarded.getBody()).isEqualTo(BODY);
        assertThat(failed.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(failed.getBody()).isEqualTo(BODY);
    }

    @Test
    @DisplayName("una plantilla desconocida no lanza y conserva el cuerpo")
    void unknownTemplateDoesNotThrow() {
        Notification notification = notification("PLANTILLA_QUE_NO_EXISTE");

        assertThatCode(() -> notification.markSent(Instant.now())).doesNotThrowAnyException();
        assertThat(notification.getBody()).isEqualTo(BODY);
    }

    private static Notification notification(String template) {
        return Notification.builder()
                .id(1L)
                .template(template)
                .channel(NotificationChannel.EMAIL)
                .recipient("docente@frc.utn.edu.ar")
                .subject("Asunto")
                .body(BODY)
                .idempotencyKey("k")
                .build();
    }
}
