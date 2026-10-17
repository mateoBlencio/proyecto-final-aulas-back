package ar.edu.utn.frc.siga.optimizer.constraint;

import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ar.edu.utn.frc.siga.optimizer.model.ClassAllocation;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerEvent;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerRoom;
import ar.edu.utn.frc.siga.optimizer.model.ScheduleSolution;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PreferRoomWithComputers")
class PreferRoomWithComputersTest {

    public static class OnlyConstraint implements ConstraintProvider {
        @Override
        public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory factory) {
            return new Constraint[]{new PreferRoomWithComputers().define(factory)};
        }
    }

    private static final SolutionManager<ScheduleSolution, HardMediumSoftScore> MANAGER =
            SolutionManager.create(SolverFactory.<ScheduleSolution>create(new SolverConfig()
                    .withSolutionClass(ScheduleSolution.class)
                    .withEntityClasses(ClassAllocation.class)
                    .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                            .withConstraintProviderClass(OnlyConstraint.class))));

    private static OptimizerEvent event(int computersNeeded) {
        return new OptimizerEvent("e", null, 20, LocalTime.of(8, 0), LocalTime.of(10, 0),
                Set.of(LocalDate.of(2026, 3, 2)), Set.of(), null, false, computersNeeded);
    }

    private static OptimizerRoom room(int computers) {
        return new OptimizerRoom(1L, 40, 100L, true, Set.of(), false, computers);
    }

    @Test
    @DisplayName("penaliza una unidad por cada computadora faltante")
    void missingComputers() {
        assertThat(softScore(allocation(event(20), room(15), false))).isEqualTo(-5);
    }

    @Test
    @DisplayName("aula sin computadoras penaliza todas las pedidas")
    void roomWithoutComputers() {
        assertThat(softScore(allocation(event(20), room(0), false))).isEqualTo(-20);
    }

    @Test
    @DisplayName("aula con computadoras de sobra no penaliza")
    void enoughComputers() {
        assertThat(softScore(allocation(event(20), room(25), false))).isZero();
    }

    @Test
    @DisplayName("evento que no pide computadoras no penaliza")
    void computersNotRequested() {
        assertThat(softScore(allocation(event(0), room(0), false))).isZero();
    }

    @Test
    @DisplayName("las asignaciones fijadas no penalizan")
    void pinnedIgnored() {
        assertThat(softScore(allocation(event(20), room(0), true))).isZero();
    }

    private static ClassAllocation allocation(OptimizerEvent event, OptimizerRoom room, boolean pinned) {
        if (pinned) {
            return ClassAllocation.pinned(event, room, Set.of());
        }
        ClassAllocation allocation = new ClassAllocation(event, List.of(room), Set.of());
        allocation.setClassroom(room);
        return allocation;
    }

    private static long softScore(ClassAllocation... allocations) {
        List<OptimizerRoom> rooms = java.util.Arrays.stream(allocations).map(ClassAllocation::getClassroom).toList();
        ScheduleSolution solution = new ScheduleSolution(rooms, List.of(allocations), ConstraintWeightOverrides.none());
        MANAGER.update(solution);
        return solution.getScore().softScore();
    }
}
