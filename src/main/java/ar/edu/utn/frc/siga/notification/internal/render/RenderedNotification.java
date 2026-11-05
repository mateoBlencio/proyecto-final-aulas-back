package ar.edu.utn.frc.siga.notification.internal.render;

import java.util.List;

public record RenderedNotification(String subject, String body, List<String> replyTo) {
}
