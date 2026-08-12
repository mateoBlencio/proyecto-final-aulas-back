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

    private long unimprovedSecondsLimit = 10;

    private EnvironmentMode environmentMode = EnvironmentMode.PHASE_ASSERT;

    private Weights weights = new Weights();

    @Getter
    @Setter
    public static class Weights {
        private int overcrowding = 100_000;
        private int sameCommissionDiffRoom = 2_000;
        private int sameCommissionDiffBuilding = 4_000;
    }
}
