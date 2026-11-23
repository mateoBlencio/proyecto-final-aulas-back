package ar.edu.utn.frc.siga.realtime.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LiveProperties.class)
class LiveConfiguration {
}
