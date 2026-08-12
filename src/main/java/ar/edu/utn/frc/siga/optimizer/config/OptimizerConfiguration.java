package ar.edu.utn.frc.siga.optimizer.config;

import ar.edu.utn.frc.siga.optimizer.model.ClassAllocation;
import ar.edu.utn.frc.siga.optimizer.model.ScheduleSolution;
import ar.edu.utn.frc.siga.optimizer.service.impl.ClassroomConstraintProvider;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.SolverManagerConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring de Timefold: define el modelo de planificación, el {@code ConstraintProvider}
 * y los beans {@link SolverFactory}/{@link SolverManager} que orquestan los solves.
 */
@Configuration
@EnableConfigurationProperties(OptimizerProperties.class)
public class OptimizerConfiguration {

    /**
     * Tope de seguridad si un solve llega sin terminación por request;
     * coincide con el máximo permitido en PreviewRequestDto (@Max(300)).
     */
    private static final long DEFAULT_SECONDS_LIMIT = 300L;

    /** Fábrica de solvers configurada con el modelo, las restricciones y el modo de entorno. */
    @Bean
    public SolverFactory<ScheduleSolution> scheduleSolverFactory(OptimizerProperties properties) {
        OptimizerProperties.Weights weights = properties.getWeights();
        ScoreDirectorFactoryConfig scoreDirectorFactoryConfig = new ScoreDirectorFactoryConfig()
                .withConstraintProviderClass(ClassroomConstraintProvider.class)
                .withConstraintProviderCustomProperties(Map.of(
                        "overcrowdingWeight", String.valueOf(weights.getOvercrowding()),
                        "sameCommissionDiffRoomWeight", String.valueOf(weights.getSameCommissionDiffRoom()),
                        "sameCommissionDiffBuildingWeight", String.valueOf(weights.getSameCommissionDiffBuilding())));
        SolverConfig config = new SolverConfig()
                .withSolutionClass(ScheduleSolution.class)
                .withEntityClasses(ClassAllocation.class)
                .withScoreDirectorFactory(scoreDirectorFactoryConfig)
                .withEnvironmentMode(properties.getEnvironmentMode())
                .withTerminationConfig(new TerminationConfig()
                        .withSecondsSpentLimit(DEFAULT_SECONDS_LIMIT));
        return SolverFactory.create(config);
    }

    /** Administra los solves concurrentes (uno por preview) sobre la fábrica configurada. */
    @Bean
    public SolverManager<ScheduleSolution> scheduleSolverManager(
            SolverFactory<ScheduleSolution> solverFactory, OptimizerProperties properties) {
        SolverManagerConfig managerConfig = new SolverManagerConfig()
                .withParallelSolverCount(properties.getParallelSolverCount());
        return SolverManager.create(solverFactory, managerConfig);
    }
}
