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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OptimizerProperties.class)
public class OptimizerConfiguration {

    @Bean
    public SolverFactory<ScheduleSolution> scheduleSolverFactory(
            OptimizerProperties properties, OptimizerSettings settings) {
        ScoreDirectorFactoryConfig scoreDirectorFactoryConfig = new ScoreDirectorFactoryConfig()
                .withConstraintProviderClass(ClassroomConstraintProvider.class);
        SolverConfig config = new SolverConfig()
                .withSolutionClass(ScheduleSolution.class)
                .withEntityClasses(ClassAllocation.class)
                .withScoreDirectorFactory(scoreDirectorFactoryConfig)
                .withEnvironmentMode(properties.getEnvironmentMode())
                .withTerminationConfig(new TerminationConfig()
                        .withSecondsSpentLimit(settings.getSolverSecondsSpentLimit()));
        return SolverFactory.create(config);
    }

    @Bean
    public SolverManager<ScheduleSolution> scheduleSolverManager(
            SolverFactory<ScheduleSolution> solverFactory, OptimizerProperties properties) {
        SolverManagerConfig managerConfig = new SolverManagerConfig()
                .withParallelSolverCount(properties.getParallelSolverCount());
        return SolverManager.create(solverFactory, managerConfig);
    }
}
