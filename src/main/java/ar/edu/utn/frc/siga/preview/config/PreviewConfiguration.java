package ar.edu.utn.frc.siga.preview.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PreviewProperties.class)
public class PreviewConfiguration {
}
