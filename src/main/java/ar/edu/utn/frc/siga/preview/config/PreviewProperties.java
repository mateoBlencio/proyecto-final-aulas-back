package ar.edu.utn.frc.siga.preview.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Propiedades de configuración del módulo preview, bajo el prefijo {@code siga.preview} de application.yaml. */
@Getter
@Setter
@ConfigurationProperties(prefix = "siga.preview")
public class PreviewProperties {

    /** Preview generada y guardada para confirmarla después. El TTL acota la obsolescencia (bound de staleness), no es eviction por memoria. */
    private long ttlMinutes = 30;
}
