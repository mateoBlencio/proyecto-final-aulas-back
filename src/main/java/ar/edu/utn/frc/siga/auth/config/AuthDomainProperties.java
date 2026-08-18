package ar.edu.utn.frc.siga.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "siga.auth")
public class AuthDomainProperties {

    private static final String ALLOWED_DOMAIN = "frc.utn.edu.ar";

    public boolean isAllowedEmail(String email) {
        return email != null && email.toLowerCase().endsWith("@" + ALLOWED_DOMAIN.toLowerCase());
    }
}
