package ar.edu.utn.frc.siga.notification.internal.render;

import ar.edu.utn.frc.siga.notification.api.NotificationTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Map;

@Component
public class NotificationRenderer {

    private final TemplateEngine templateEngine;

    public NotificationRenderer(@Qualifier("notificationTemplateEngine") TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public RenderedNotification render(NotificationTemplate template, Map<String, Object> model) {
        Context context = new Context();
        context.setVariables(model);
        String baseName = baseNameFor(template);
        String subject = templateEngine.process(baseName + "-subject", context).strip();
        String body = templateEngine.process(baseName + "-body", context);
        return new RenderedNotification(subject, body);
    }

    private static String baseNameFor(NotificationTemplate template) {
        return template.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
