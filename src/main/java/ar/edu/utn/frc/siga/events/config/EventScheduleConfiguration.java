package ar.edu.utn.frc.siga.events.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EventScheduleProperties.class)
public class EventScheduleConfiguration {
}
