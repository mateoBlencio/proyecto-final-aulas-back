package ar.edu.utn.frc.siga.dashboard.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("siga.dashboard")
public record DashboardProperties(
        @Min(1) @Max(100) int underusedThresholdPercent,
        @NotNull @Valid Snapshot snapshot) {

    public record Snapshot(boolean enabled, String cron) {
    }
}
