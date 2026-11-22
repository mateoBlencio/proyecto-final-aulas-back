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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.NonNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PreferSameBuildingSameEvent")
class PreferSameBuildingSameEventTest {

    public static class OnlyConstraint implements ConstraintProvider {
        @Override
        public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory factory) {
            return new Constraint[]{new PreferSameBuildingSameEvent().define(factory)};
        }
    }

    private static final SolutionManager<ScheduleSolution, HardMediumSoftScore> MANAGER =
            SolutionManager.create(SolverFactory.<ScheduleSolution>create(new SolverConfig()
                    .withSolutionClass(ScheduleSolution.class)
                    .withEntityClasses(ClassAllocation.class)
                    .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                            .withConstraintProviderClass(OnlyConstraint.class))));

    private static final OptimizerRoom ROOM_A1 = new OptimizerRoom(1L, 40, 100L);
    private static final OptimizerRoom ROOM_A2 = new OptimizerRoom(2L, 40, 100L);
    private static final OptimizerRoom ROOM_B = new OptimizerRoom(3L, 40, 200L);

    private static ClassAllocation mirror(String id, String groupKey, OptimizerRoom room) {
        OptimizerEvent event = new OptimizerEvent(id, "K1", 20, LocalTime.of(8, 0), LocalTime.of(10, 0),
                Set.of(LocalDate.of(2026, 3, 2)), Set.of(), groupKey);
        ClassAllocation allocation = new ClassAllocation(event, List.of(room), Set.of());
        allocation.setClassroom(room);
        return allocation;
    }

    private static long softScore(ClassAllocation... allocations) {
        ScheduleSolution solution = new ScheduleSolution(
                List.of(ROOM_A1, ROOM_A2, ROOM_B), List.of(allocations), ConstraintWeightOverrides.none());
        MANAGER.update(solution);
        return solution.getScore().softScore();
    }

    @Test
    @DisplayName("espejos del mismo evento en edificios distintos penalizan 1")
    void differentBuildings() {
        assertThat(softScore(mirror("e-0", "g1", ROOM_A1), mirror("e-1", "g1", ROOM_B))).isEqualTo(-1);
    }

    @Test
    @DisplayName("espejos del mismo evento en el mismo edificio no penalizan")
    void sameBuilding() {
        assertThat(softScore(mirror("e-0", "g1", ROOM_A1), mirror("e-1", "g1", ROOM_A2))).isZero();
    }

    @Test
    @DisplayName("groupKey null no penaliza aunque los edificios difieran")
    void nullGroupKey() {
        assertThat(softScore(mirror("e-0", null, ROOM_A1), mirror("e-1", null, ROOM_B))).isZero();
    }

    @Test
    @DisplayName("groupKey distintos en edificios distintos no penalizan")
    void differentGroups() {
        assertThat(softScore(mirror("e-0", "g1", ROOM_A1), mirror("e-1", "g2", ROOM_B))).isZero();
    }
}
