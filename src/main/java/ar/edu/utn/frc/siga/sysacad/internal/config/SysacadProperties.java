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

    // TEMPORAL: sirve las asignaciones desde un fixture en vez de la vista real HorariosComisionesCupos,
    // para tener asignaciones durante el desarrollo. Apagar (o borrar el mock) cuando las vistas reales
    // estén completas y alineadas. Mismo criterio que materias-mock-enabled.
    private boolean asignacionesMockEnabled = false;

    private Sync sync = new Sync(null, null, null, null, null, null, null);

    public record Sync(
            String buildings,
            String classrooms,
            String specialties,
            String subjects,
            String commissions,
            String events,
            String allocations
    ) {}
}
