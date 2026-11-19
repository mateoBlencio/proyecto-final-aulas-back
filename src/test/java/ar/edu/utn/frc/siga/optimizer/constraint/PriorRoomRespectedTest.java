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

@DisplayName("PriorRoomRespected")
class PriorRoomRespectedTest {

    public static class OnlyConstraint implements ConstraintProvider {
        @Override
        public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory factory) {
            return new Constraint[]{new PriorRoomRespected().define(factory)};
        }
    }

    private static final SolutionManager<ScheduleSolution, HardMediumSoftScore> MANAGER =
            SolutionManager.create(SolverFactory.<ScheduleSolution>create(new SolverConfig()
                    .withSolutionClass(ScheduleSolution.class)
                    .withEntityClasses(ClassAllocation.class)
                    .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                            .withConstraintProviderClass(OnlyConstraint.class))));

    private static final OptimizerRoom ROOM_R = new OptimizerRoom(368L, 40, 100L);
    private static final OptimizerRoom ROOM_OTHER = new OptimizerRoom(369L, 40, 100L);

    private static OptimizerEvent event(String id, Long priorRoomId) {
        return new OptimizerEvent(id, "K1", 20, LocalTime.of(19, 55), LocalTime.of(22, 20),
                Set.of(LocalDate.of(2026, 3, 4)), Set.of(), null, false, 0, priorRoomId);
    }

    private static ClassAllocation allocation(OptimizerEvent event, OptimizerRoom room, Set<String> conflicts) {
        ClassAllocation allocation = new ClassAllocation(event, List.of(ROOM_R, ROOM_OTHER), conflicts);
        allocation.setClassroom(room);
        return allocation;
    }

    private static long hardScore(ClassAllocation... allocations) {
        ScheduleSolution solution = new ScheduleSolution(
                List.of(ROOM_R, ROOM_OTHER), List.of(allocations), ConstraintWeightOverrides.none());
        MANAGER.update(solution);
        return solution.getScore().hardScore();
    }

    @Test
    @DisplayName("dueño sin aula y otro evento solapado en su aula previa: penaliza 1 hard")
    void ownerUnassignedAndOtherInPriorRoom() {
        ClassAllocation owner = allocation(event("owner", 368L), null, Set.of("other"));
        ClassAllocation other = allocation(event("other", null), ROOM_R, Set.of("owner"));

        assertThat(hardScore(owner, other)).isEqualTo(-1);
    }

    @Test
    @DisplayName("dos eventos solapados ocupan el aula previa: penaliza 2 hard")
    void twoOthersInPriorRoom() {
        ClassAllocation owner = allocation(event("owner", 368L), null, Set.of("o1", "o2"));
        ClassAllocation o1 = allocation(event("o1", null), ROOM_R, Set.of("owner"));
        ClassAllocation o2 = allocation(event("o2", null), ROOM_R, Set.of("owner"));

        assertThat(hardScore(owner, o1, o2)).isEqualTo(-2);
    }

    @Test
    @DisplayName("el otro evento no se superpone con el dueño: no penaliza")
    void noOverlap() {
        ClassAllocation owner = allocation(event("owner", 368L), null, Set.of());
        ClassAllocation other = allocation(event("other", null), ROOM_R, Set.of());

        assertThat(hardScore(owner, other)).isZero();
    }

    @Test
    @DisplayName("el dueño ya tiene aula: no penaliza aunque el otro esté en su aula previa")
    void ownerHasRoom() {
        ClassAllocation owner = allocation(event("owner", 368L), ROOM_OTHER, Set.of("other"));
        ClassAllocation other = allocation(event("other", null), ROOM_R, Set.of("owner"));

        assertThat(hardScore(owner, other)).isZero();
    }

    @Test
    @DisplayName("el otro evento está en otra aula: no penaliza")
    void otherInDifferentRoom() {
        ClassAllocation owner = allocation(event("owner", 368L), null, Set.of("other"));
        ClassAllocation other = allocation(event("other", null), ROOM_OTHER, Set.of("owner"));

        assertThat(hardScore(owner, other)).isZero();
    }

    @Test
    @DisplayName("el dueño no tiene aula previa: no penaliza")
    void noPriorRoom() {
        ClassAllocation owner = allocation(event("owner", null), null, Set.of("other"));
        ClassAllocation other = allocation(event("other", null), ROOM_R, Set.of("owner"));

        assertThat(hardScore(owner, other)).isZero();
    }

    @Test
    @DisplayName("el otro evento sin aula: no penaliza")
    void otherUnassigned() {
        ClassAllocation owner = allocation(event("owner", 368L), null, Set.of("other"));
        ClassAllocation other = allocation(event("other", null), null, Set.of("owner"));

        assertThat(hardScore(owner, other)).isZero();
    }
}
