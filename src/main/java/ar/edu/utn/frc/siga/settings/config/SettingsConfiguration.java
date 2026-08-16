package ar.edu.utn.frc.siga.settings.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SettingsCatalogProperties.class)
public class SettingsConfiguration {
}
