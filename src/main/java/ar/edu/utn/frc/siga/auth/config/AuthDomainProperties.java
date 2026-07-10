package ar.edu.utn.frc.siga.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "siga.auth")
public class AuthDomainProperties {

    private String allowedEmailDomain = "frc.utn.edu.ar";
}
