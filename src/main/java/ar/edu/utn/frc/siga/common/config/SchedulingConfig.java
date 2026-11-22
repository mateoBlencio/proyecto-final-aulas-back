package ar.edu.utn.frc.siga.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Antes solo vivía en SysacadConfiguration, condicionada a SYSACAD_ENABLED=true: con el flag en false ningún @Scheduled de la app corría. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
