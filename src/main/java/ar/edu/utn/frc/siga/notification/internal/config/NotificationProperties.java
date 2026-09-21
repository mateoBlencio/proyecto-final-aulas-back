package ar.edu.utn.frc.siga.notification.internal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "siga.notifications")
public class NotificationProperties {

    private int maxAttempts = 5;

    private Duration retryInterval = Duration.ofMinutes(1);

    private Email email = new Email();

    @Getter
    @Setter
    public static class Email {
        private boolean enabled = false;
        private String from;
    }
}
