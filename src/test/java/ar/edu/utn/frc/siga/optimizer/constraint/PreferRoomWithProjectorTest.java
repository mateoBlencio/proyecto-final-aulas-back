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

@DisplayName("PreferRoomWithProjector")
class PreferRoomWithProjectorTest {

    public static class OnlyConstraint implements ConstraintProvider {
        @Override
        public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory factory) {
            return new Constraint[]{new PreferRoomWithProjector().define(factory)};
        }
    }

    private static final SolutionManager<ScheduleSolution, HardMediumSoftScore> MANAGER =
            SolutionManager.create(SolverFactory.<ScheduleSolution>create(new SolverConfig()
                    .withSolutionClass(ScheduleSolution.class)
                    .withEntityClasses(ClassAllocation.class)
                    .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                            .withConstraintProviderClass(OnlyConstraint.class))));

    private static OptimizerEvent event(boolean needsProjector) {
        return new OptimizerEvent("e", null, 20, LocalTime.of(8, 0), LocalTime.of(10, 0),
                Set.of(LocalDate.of(2026, 3, 2)), Set.of(), null, needsProjector, 0);
    }

    private static OptimizerRoom room(boolean hasProjector) {
        return new OptimizerRoom(1L, 40, 100L, true, Set.of(), hasProjector, 0);
    }

    @Test
    @DisplayName("aula sin proyector para un evento que lo pide penaliza 1")
    void roomWithoutProjector() {
        assertThat(softScore(allocation(event(true), room(false), false))).isEqualTo(-1);
    }

    @Test
    @DisplayName("aula con proyector no penaliza")
    void roomWithProjector() {
        assertThat(softScore(allocation(event(true), room(true), false))).isZero();
    }

    @Test
    @DisplayName("evento que no pide proyector no penaliza aunque el aula no lo tenga")
    void projectorNotRequested() {
        assertThat(softScore(allocation(event(false), room(false), false))).isZero();
    }

    @Test
    @DisplayName("las asignaciones fijadas no penalizan")
    void pinnedIgnored() {
        assertThat(softScore(allocation(event(true), room(false), true))).isZero();
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
