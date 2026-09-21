package ar.edu.utn.frc.siga.notification.api;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface NotificationSender {
    NotificationResult send(NotificationRequest request);
}
