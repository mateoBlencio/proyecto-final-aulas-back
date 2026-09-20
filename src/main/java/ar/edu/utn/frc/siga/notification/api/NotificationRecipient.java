package ar.edu.utn.frc.siga.notification.api;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record NotificationRecipient(String displayName, String email) {
}
