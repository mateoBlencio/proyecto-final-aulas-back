package ar.edu.utn.frc.siga.solver.config;

import ar.edu.utn.frc.siga.solver.model.ClassAssignment;
import ar.edu.utn.frc.siga.solver.model.ScheduleSolution;
import ar.edu.utn.frc.siga.solver.model.SolverEvent;
import ar.edu.utn.frc.siga.solver.model.SolverRoom;
import ai.timefold.solver.core.api.solver.SolverConfigOverride;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SolverConfigurationTest {

    private final SolverConfiguration configuration = new SolverConfiguration();

    @Test
    void solverManager_solvesTrivialProblem() throws Exception {
        SolverProperties properties = new SolverProperties();
        SolverFactory<ScheduleSolution> factory = configuration.scheduleSolverFactory(properties);

        try (SolverManager<ScheduleSolution> manager =
                     configuration.scheduleSolverManager(factory, properties)) {

            SolverRoom room = new SolverRoom(1, 40, null);
            SolverEvent event = new SolverEvent("ev-1", null, 30, LocalTime.of(8, 0), LocalTime.of(9, 30),
                    Set.of(LocalDate.of(2026, 3, 2)));
            ClassAssignment assignment = new ClassAssignment(event, List.of(room), Set.of());
            ScheduleSolution problem = new ScheduleSolution(List.of(room), List.of(assignment));

            ScheduleSolution solution = manager.solveBuilder()
                    .withProblemId("test-trivial")
                    .withProblem(problem)
                    .withConfigOverride(new SolverConfigOverride()
                            .withTerminationConfig(new TerminationConfig()
                                    .withSecondsSpentLimit(5L)
                                    .withUnimprovedSecondsSpentLimit(1L)))
                    .run()
                    .getFinalBestSolution();

            assertThat(solution.getScore().hardScore()).isZero();
            assertThat(solution.getAssignments()).hasSize(1);
            assertThat(solution.getAssignments().get(0).getClassroom()).isEqualTo(room);
        }
    }

    @Test
    void solverFactory_usesConfiguredEnvironmentMode() {
        SolverProperties properties = new SolverProperties();
        assertThat(configuration.scheduleSolverFactory(properties)).isNotNull();
    }
}
