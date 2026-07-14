package ar.edu.utn.frc.siga.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "siga.login-ratelimit")
public class LoginRateLimitProperties {

    private int maxAttempts = 5;

    private int windowMinutes = 15;
}
