package ar.edu.utn.frc.siga.notification.internal.render;

import java.util.List;

public record RenderedNotification(String subject, String body, List<String> replyTo, List<String> cc,
                                   List<String> bcc) {

    public RenderedNotification(String subject, String body, List<String> replyTo) {
        this(subject, body, replyTo, List.of(), List.of());
    }
}
