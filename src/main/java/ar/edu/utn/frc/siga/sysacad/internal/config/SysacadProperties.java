package ar.edu.utn.frc.siga.sysacad.internal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import org.springframework.util.unit.DataSize;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "siga.sysacad")
public class SysacadProperties {

    private static final int DEFAULT_MIN_VALID_ROWS_PERCENT = 50;

    private boolean enabled = false;

    private String baseUrl;

    private String apiKey;

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(30);

    private int maxRetries = 3;

    private DataSize maxResponseSize = DataSize.ofMegabytes(32);

    private Sync sync = new Sync(null, DEFAULT_MIN_VALID_ROWS_PERCENT);

    /**
     * {@code minValidRowsPercent}: porcentaje mínimo de filas válidas, respecto de lo vigente en SIGA, para
     * que una vista dé de baja lo ausente. 0 desactiva el umbral (cero filas válidas aborta siempre).
     */
    public record Sync(String cron, @DefaultValue("50") int minValidRowsPercent) {}
}
