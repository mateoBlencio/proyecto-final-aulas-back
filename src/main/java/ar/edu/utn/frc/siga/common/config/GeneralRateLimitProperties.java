package ar.edu.utn.frc.siga.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "siga.ratelimit.general")
public class GeneralRateLimitProperties {

    /** Capacidad del bucket por IP: cantidad máxima de requests por período de refill. */
    private long capacity = 100;

    /** Período de refill del bucket, en segundos. */
    private long refillPeriodSeconds = 60;
}
