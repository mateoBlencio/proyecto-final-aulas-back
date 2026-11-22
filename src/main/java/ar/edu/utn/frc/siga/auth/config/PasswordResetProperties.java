package ar.edu.utn.frc.siga.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "siga.password-reset")
public class PasswordResetProperties {

    @Positive
    private int tokenTtlMinutes = 30;

    @Positive
    private int maxRequestsPerEmailPerIp = 3;

    @Positive
    private int maxRequestsPerIp = 20;

    @Positive
    private int maxResetAttemptsPerIp = 10;

    @Positive
    private int windowMinutes = 60;

    @NotBlank
    @Pattern(regexp = ".*[^/]$")
    private String frontendBaseUrl;
}
