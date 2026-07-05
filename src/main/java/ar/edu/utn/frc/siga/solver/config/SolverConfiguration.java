package ar.edu.utn.frc.siga.solver.config;

import ar.edu.utn.frc.siga.solver.optimization.ClassAssignment;
import ar.edu.utn.frc.siga.solver.optimization.ClassroomConstraintProvider;
import ar.edu.utn.frc.siga.solver.optimization.ScheduleSolution;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.SolverManagerConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SolverProperties.class)
public class SolverConfiguration {

    /**
     * Tope de seguridad si un solve llega sin terminación por request;
     * coincide con el máximo permitido en AllocationParametersDto.
     */
    private static final long DEFAULT_SECONDS_LIMIT = 300L;

    @Bean
    public SolverFactory<ScheduleSolution> scheduleSolverFactory(SolverProperties properties) {
        SolverConfig config = new SolverConfig()
                .withSolutionClass(ScheduleSolution.class)
                .withEntityClasses(ClassAssignment.class)
                .withConstraintProviderClass(ClassroomConstraintProvider.class)
                .withEnvironmentMode(properties.getEnvironmentMode())
                .withTerminationConfig(new TerminationConfig()
                        .withSecondsSpentLimit(DEFAULT_SECONDS_LIMIT));
        return SolverFactory.create(config);
    }

    @Bean
    public SolverManager<ScheduleSolution> scheduleSolverManager(
            SolverFactory<ScheduleSolution> solverFactory, SolverProperties properties) {
        SolverManagerConfig managerConfig = new SolverManagerConfig()
                .withParallelSolverCount(properties.getParallelSolverCount());
        return SolverManager.create(solverFactory, managerConfig);
    }
}
