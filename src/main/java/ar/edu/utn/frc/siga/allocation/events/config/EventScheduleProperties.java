package ar.edu.utn.frc.siga.allocation.events.config;

import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Ventana horaria válida para eventos académicos, bajo el prefijo {@code siga.events.hours}. */
@Getter
@Setter
@ConfigurationProperties(prefix = "siga.events.hours")
public class EventScheduleProperties {

    /** Hora más temprana en que puede empezar un evento. */
    private LocalTime start = LocalTime.of(8, 0);

    /** Hora más tardía en que puede terminar un evento. */
    private LocalTime end = LocalTime.of(23, 0);
}
