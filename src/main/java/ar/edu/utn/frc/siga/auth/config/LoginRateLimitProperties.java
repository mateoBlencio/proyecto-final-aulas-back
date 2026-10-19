package ar.edu.utn.frc.siga.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "siga.login-ratelimit")
public class LoginRateLimitProperties {

    private int maxAttempts = 5;

    // Failures allowed per client IP across all emails within the window
    private int maxAttemptsPerIp = 50;

    private int windowMinutes = 15;
}
