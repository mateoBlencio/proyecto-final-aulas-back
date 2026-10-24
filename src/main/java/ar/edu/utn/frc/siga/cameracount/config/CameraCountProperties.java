package ar.edu.utn.frc.siga.cameracount.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "siga.camera-count")
public class CameraCountProperties {

    private boolean enabled = false;

    @NotBlank
    @Pattern(regexp = "https?://.+", message = "debe ser una URL http o https")
    private String url;

    @NotBlank
    private String apiKey;

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(90);
}
