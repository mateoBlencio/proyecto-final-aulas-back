package ar.edu.utn.frc.siga.notification.api;

import org.springframework.modulith.NamedInterface;

import java.util.List;
import java.util.Map;

@NamedInterface("api")
public record NotificationRequest(
        NotificationTemplate template,
        List<NotificationRecipient> recipients,
        Map<String, Object> model,
        String idempotencyKey) {
}
