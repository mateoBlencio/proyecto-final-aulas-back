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
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SolverManagerProvider {

    private final OptimizerSettings optimizerSettings;
    private final OptimizerProperties optimizerProperties;

    private volatile SolverManager<ScheduleSolution> solverManager;

    @PostConstruct
    void init() {
        this.solverManager = build();
    }

    public SolverManager<ScheduleSolution> get() {
        return solverManager;
    }

    public synchronized void rebuild() {
        SolverManager<ScheduleSolution> previous = this.solverManager;
        this.solverManager = build();
        if (previous != null) {
            previous.close();
        }
    }

    private SolverManager<ScheduleSolution> build() {
        ScoreDirectorFactoryConfig scoreDirectorFactoryConfig = new ScoreDirectorFactoryConfig()
                .withConstraintProviderClass(ClassroomConstraintProvider.class)
                .withConstraintProviderCustomProperties(Map.of(
                        "overcrowdingWeight",
                        String.valueOf(optimizerSettings.getOvercrowdingWeight()),
                        "sameCommissionDiffRoomWeight",
                        String.valueOf(optimizerSettings.getSameCommissionDiffRoomWeight()),
                        "sameCommissionDiffBuildingWeight",
                        String.valueOf(optimizerSettings.getSameCommissionDiffBuildingWeight()),
                        "unusedCapacityWeight",
                        String.valueOf(optimizerSettings.getUnusedCapacityWeight())));
        SolverConfig config = new SolverConfig()
                .withSolutionClass(ScheduleSolution.class)
                .withEntityClasses(ClassAllocation.class)
                .withScoreDirectorFactory(scoreDirectorFactoryConfig)
                .withEnvironmentMode(optimizerProperties.getEnvironmentMode())
                .withTerminationConfig(new TerminationConfig()
                        .withSecondsSpentLimit(optimizerSettings.getSolverSecondsSpentLimit()));
        SolverFactory<ScheduleSolution> solverFactory = SolverFactory.create(config);
        SolverManagerConfig managerConfig = new SolverManagerConfig()
                .withParallelSolverCount(optimizerProperties.getParallelSolverCount());
        return SolverManager.create(solverFactory, managerConfig);
    }
}
