package ar.edu.utn.frc.siga.notification.api;

import org.springframework.modulith.NamedInterface;

import java.util.List;
import java.util.Map;

/** {@code cc} y {@code bcc} van en copia y copia oculta de cada mail; nunca entran en la lista de destinatarios. */
@NamedInterface("api")
public record NotificationRequest(
        NotificationTemplate template,
        List<NotificationRecipient> recipients,
        Map<String, Object> model,
        String idempotencyKey,
        List<String> replyTo,
        List<String> cc,
        List<String> bcc) {

    public NotificationRequest(NotificationTemplate template, List<NotificationRecipient> recipients,
                               Map<String, Object> model, String idempotencyKey) {
        this(template, recipients, model, idempotencyKey, List.of(), List.of(), List.of());
    }

    public NotificationRequest(NotificationTemplate template, List<NotificationRecipient> recipients,
                               Map<String, Object> model, String idempotencyKey, List<String> replyTo) {
        this(template, recipients, model, idempotencyKey, replyTo, List.of(), List.of());
    }
}
