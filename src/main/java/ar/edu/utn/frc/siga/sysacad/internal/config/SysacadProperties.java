package ar.edu.utn.frc.siga.sysacad.internal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "siga.sysacad")
public class SysacadProperties {

    private boolean enabled = false;

    private String baseUrl;

    private String apiKey;

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(30);

    private int maxRetries = 3;

    private boolean materiasMockEnabled = false;

    private Sync sync = new Sync(null, null, null, null, null, null, null);

    public record Sync(
            String edificios,
            String aulas,
            String especialidades,
            String materias,
            String comisiones,
            String eventos,
            String asignaciones
    ) {}
}
