package ar.edu.utn.frc.siga.sysacad.internal.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "siga.sysacad")
public class SysacadProperties {

    private static final int DEFAULT_MIN_VALID_ROWS_PERCENT = 50;

    private boolean enabled = false;

    @Pattern(regexp = "https://.+", message = "debe ser una URL https")
    private String baseUrl;

    @NotBlank
    private String apiKey;

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(30);

    private int maxRetries = 3;

    private DataSize maxResponseSize = DataSize.ofMegabytes(32);

    private List<@Valid @NotNull MessageCommission> commissionMessageTestCommissions = List.of();

    private int commissionMessagePublisherLegajo = 0;

    @NotBlank
    private String commissionMessagePublisherUserType = "S";

    @Valid
    private Sync sync = new Sync(DEFAULT_MIN_VALID_ROWS_PERCENT, Sync.DEFAULT_ZONE);

    @Valid
    private Write write = Write.defaults();

    public record MessageCommission(
            @NotNull Integer specialty,
            @NotNull Integer plan,
            @NotNull Integer subject,
            @NotNull Integer commission
    ) {

        public boolean matches(Integer specialty, Integer plan, Integer subject, Integer commission) {
            return this.specialty.equals(specialty) && this.plan.equals(plan)
                    && this.subject.equals(subject) && this.commission.equals(commission);
        }
    }

    /**
     * {@code minValidRowsPercent}: minimum percentage of valid rows, relative to what is active in SIGA, for a
     * view to deactivate what is absent. 0 disables the threshold (zero valid rows always aborts).
     */
    public record Sync(
            @DefaultValue("50") @Min(0) @Max(100) int minValidRowsPercent,
            @DefaultValue("America/Argentina/Buenos_Aires") @NotNull ZoneId zone
    ) {

        public static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    }

    public record Write(
            @DefaultValue("5") @Min(1) int maxAttempts,
            @DefaultValue("3") @Min(1) int maxConsecutiveFailures,
            @DefaultValue({"PT5M", "PT15M", "PT30M", "PT1H"}) @NotEmpty List<Duration> retryBackoff,
            @DefaultValue("PT15S") @NotNull Duration readTimeout,
            @DefaultValue("PT2S") @NotNull Duration inCallRetryDelay,
            @DefaultValue("PT1H") @NotNull Duration outageAlertAfter,
            @DefaultValue("P1D") @NotNull Duration outageAlertRepeat,
            @DefaultValue("30") @Min(1) int maxSendsPerRun,
            @DefaultValue("30") @Min(1) int maxRequestsPerMinute,
            @DefaultValue("P7D") @NotNull Duration recentSendWindow,
            @DefaultValue("PT24H") @NotNull Duration waitingVisibleBefore,
            @DefaultValue("PT1H") @NotNull Duration afterSyncMargin,
            @DefaultValue("P1D") @NotNull Duration noCronRetryDelay,
            @DefaultValue("30") @Min(1) int alertMailMaxLines
    ) {

        public static Write defaults() {
            return new Write(5, 3,
                    List.of(Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofHours(1)),
                    Duration.ofSeconds(15), Duration.ofSeconds(2), Duration.ofHours(1), Duration.ofDays(1),
                    30, 30, Duration.ofDays(7), Duration.ofHours(24),
                    Duration.ofHours(1), Duration.ofDays(1), 30);
        }
    }
}
