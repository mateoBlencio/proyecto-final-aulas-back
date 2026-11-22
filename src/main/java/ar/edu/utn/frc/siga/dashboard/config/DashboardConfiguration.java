package ar.edu.utn.frc.siga.dashboard.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DashboardProperties.class)
public class DashboardConfiguration {
}
