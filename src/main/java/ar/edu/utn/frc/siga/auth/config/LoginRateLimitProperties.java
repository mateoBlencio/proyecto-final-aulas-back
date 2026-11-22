package ar.edu.utn.frc.siga.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "siga.login-ratelimit")
public class LoginRateLimitProperties {

    private int maxAttempts = 5;

    // Failures allowed per client IP across all emails within the window. High default: if TRUSTED_PROXIES
    // is not set the IP is the reverse proxy's, shared by every user (see TrustedProxiesStartupCheck).
    private int maxAttemptsPerIp = 200;

    // Failures for one email from any IP that trigger a WARN (never a block), to spot distributed brute force
    private int alertThresholdPerEmail = 50;

    private int windowMinutes = 15;
}
