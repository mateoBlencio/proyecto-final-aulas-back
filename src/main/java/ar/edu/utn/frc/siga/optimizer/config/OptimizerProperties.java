package ar.edu.utn.frc.siga.optimizer.config;

import ai.timefold.solver.core.config.solver.EnvironmentMode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "siga.optimizer")
public class OptimizerProperties {

    private String parallelSolverCount = "AUTO";

    private EnvironmentMode environmentMode = EnvironmentMode.PHASE_ASSERT;
}
