package ar.edu.utn.frc.siga.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "siga.ratelimit.general")
public class GeneralRateLimitProperties {

    private long capacity = 100;

    private long refillPeriodSeconds = 60;
}
