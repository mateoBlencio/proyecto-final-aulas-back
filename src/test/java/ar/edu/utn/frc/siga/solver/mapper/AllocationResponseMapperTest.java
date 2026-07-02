package ar.edu.utn.frc.siga.solver.mapper;

import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.allocation.model.AllocationStatus;
import ar.edu.utn.frc.siga.allocation.model.UniqueEvent;
import ar.edu.utn.frc.siga.solver.optimization.ClassAssignment;
import ar.edu.utn.frc.siga.solver.optimization.ScheduleSolution;
import ai.timefold.solver.core.api.score.HardSoftScore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AllocationResponseMapperTest {

    private AllocationResponseMapper mapper;
    private Method resolveStatus;
    private Method occupancyRatio;

    @BeforeEach
    void setUp() throws Exception {
        mapper = new AllocationResponseMapper(new EventMapper());

        resolveStatus = AllocationResponseMapper.class.getDeclaredMethod(
                "resolveStatus", ScheduleSolution.class, long.class);
        resolveStatus.setAccessible(true);

        occupancyRatio = AllocationResponseMapper.class.getDeclaredMethod(
                "occupancyRatio", ClassAssignment.class);
        occupancyRatio.setAccessible(true);
    }

    private AllocationStatus resolve(ScheduleSolution solution, long unassigned) throws Exception {
        return (AllocationStatus) resolveStatus.invoke(mapper, solution, unassigned);
    }

    private double ratio(ClassAssignment assignment) throws Exception {
        return (double) occupancyRatio.invoke(mapper, assignment);
    }

    private ScheduleSolution solutionWithScore(int hard, int soft) {
        ScheduleSolution sol = new ScheduleSolution(List.of(), List.of(), null);
        sol.setScore(HardSoftScore.of(hard, soft));
        return sol;
    }

    private ClassAssignment assignmentWith(int enrolled, int capacity) {
        UniqueEvent event = UniqueEvent.builder()
                .planningId("e1").enrolled(enrolled)
                .startTime(LocalTime.of(8, 0))
                .duration(Duration.ofMinutes(90))
                .date(LocalDate.of(2024, 1, 1))
                .build();
        Classroom classroom = Classroom.builder().id(1).roomNumber("C1").capacity(capacity).build();
        ClassAssignment a = new ClassAssignment(event, List.of(classroom), Set.of());
        a.setClassroom(classroom);
        return a;
    }

    // ─── resolveStatus ───────────────────────────────────────────────────────

    @Test
    void upRm001_success_hardZero_unassignedZero() throws Exception {
        assertThat(resolve(solutionWithScore(0, 0), 0)).isEqualTo(AllocationStatus.SUCCESS);
    }

    @Test
    void upRm002_partialSuccess_hardZero_unassignedTwo() throws Exception {
        assertThat(resolve(solutionWithScore(0, -100), 2)).isEqualTo(AllocationStatus.PARTIAL_SUCCESS);
    }

    @Test
    void upRm003_infeasible_hardNegative_unassignedZero() throws Exception {
        assertThat(resolve(solutionWithScore(-1, 0), 0)).isEqualTo(AllocationStatus.INFEASIBLE);
    }

    @Test
    void upRm004_infeasible_hardTakesPriority() throws Exception {
        assertThat(resolve(solutionWithScore(-3, -100), 1)).isEqualTo(AllocationStatus.INFEASIBLE);
    }

    // ─── occupancyRatio ──────────────────────────────────────────────────────

    @Test
    void upRm005_fullCapacity_ratioOne() throws Exception {
        assertThat(ratio(assignmentWith(80, 80))).isEqualTo(1.0);
    }

    @Test
    void upRm006_partial_ratio625() throws Exception {
        assertThat(ratio(assignmentWith(50, 80))).isEqualTo(50.0 / 80.0);
    }

    @Test
    void upRm007_partial_ratio5625() throws Exception {
        assertThat(ratio(assignmentWith(45, 80))).isEqualTo(45.0 / 80.0);
    }

    @Test
    void upRm008_overcrowded_ratioOne() throws Exception {
        // enrolled=165, capacity=165 (no undercrowding), unused=0 → ratio = 165/165 = 1.0
        assertThat(ratio(assignmentWith(165, 165))).isEqualTo(1.0);
    }
}
