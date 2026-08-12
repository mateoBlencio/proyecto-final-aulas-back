package ar.edu.utn.frc.siga.preview.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "siga.preview")
public class PreviewProperties {

    private long ttlMinutes = 30;
}
