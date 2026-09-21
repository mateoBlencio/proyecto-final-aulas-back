package ar.edu.utn.frc.siga.notification.api;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record NotificationResult(int queued, int skipped) {
}
