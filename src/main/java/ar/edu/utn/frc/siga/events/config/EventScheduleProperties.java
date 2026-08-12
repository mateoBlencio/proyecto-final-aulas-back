package ar.edu.utn.frc.siga.events.config;

import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "siga.events.hours")
public class EventScheduleProperties {

    private LocalTime start = LocalTime.of(8, 0);

    private LocalTime end = LocalTime.of(23, 0);
}
