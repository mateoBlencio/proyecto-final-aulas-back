package ar.edu.utn.frc.siga.optimizer.service.impl;

import ar.edu.utn.frc.siga.optimizer.config.OptimizerSettings;
import ar.edu.utn.frc.siga.optimizer.model.ClassAllocation;
import ar.edu.utn.frc.siga.optimizer.model.ScheduleSolution;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerAllocation;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerEvent;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerOccupancy;
import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerRoom;
import ai.timefold.solver.core.api.solver.SolverJob;
import ai.timefold.solver.core.api.solver.SolverJobBuilder;
import ai.timefold.solver.core.api.solver.SolverManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OptimizerServiceImpl")
class OptimizerServiceImplTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 3);
    private static final LocalDate OTHER_DATE = LocalDate.of(2026, 8, 4);

    @Mock
    private SolverManager<ScheduleSolution> solverManager;

    @Mock
    private OptimizerSettings optimizerSettings;

    private OptimizerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OptimizerServiceImpl(solverManager, optimizerSettings);
    }

    private static OptimizerEvent event(String id, LocalTime start, LocalTime end, LocalDate... dates) {
        return new OptimizerEvent(id, null, 10, start, end, Set.of(dates));
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<ScheduleSolution> stubSolverToEchoProblem() throws Exception {
        SolverJobBuilder<ScheduleSolution> builder = mock(SolverJobBuilder.class, Answers.RETURNS_SELF);
        SolverJob<ScheduleSolution> job = mock(SolverJob.class);
        ArgumentCaptor<ScheduleSolution> captor = ArgumentCaptor.forClass(ScheduleSolution.class);
        when(solverManager.solveBuilder()).thenReturn(builder);
        when(builder.withProblem(captor.capture())).thenReturn(builder);
        when(builder.run()).thenReturn(job);
        when(job.getFinalBestSolution()).thenAnswer(invocation -> captor.getValue());
        return captor;
    }

    private static ClassAllocation findByPlanningId(ScheduleSolution solution, String planningId) {
        return solution.getAllocations().stream()
                .filter(a -> a.getId().equals(planningId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró allocation con planningId=" + planningId));
    }

    @Nested
    @DisplayName("computeConflicts (vía optimize)")
    class ComputeConflicts {

        @Test
        @DisplayName("eventos que solapan en la misma fecha generan un par simétrico")
        void overlappingEventsAreSymmetricallyConflicting() throws Exception {
            OptimizerEvent a = event("a", LocalTime.of(8, 0), LocalTime.of(10, 0), DATE);
            OptimizerEvent b = event("b", LocalTime.of(9, 0), LocalTime.of(11, 0), DATE);
            OptimizerRoom room = new OptimizerRoom(1L, 50, 10L);

            ArgumentCaptor<ScheduleSolution> captor = stubSolverToEchoProblem();
            service.optimize(List.of(a, b), List.of(room), List.of(), 5);

            ScheduleSolution problem = captor.getValue();
            assertThat(findByPlanningId(problem, "a").conflictsWith("b")).isTrue();
            assertThat(findByPlanningId(problem, "b").conflictsWith("a")).isTrue();
        }

        @Test
        @DisplayName("eventos contiguos (fin de uno == inicio del otro) no conflictúan")
        void contiguousEventsDoNotConflict() throws Exception {
            OptimizerEvent a = event("a", LocalTime.of(8, 0), LocalTime.of(10, 0), DATE);
            OptimizerEvent b = event("b", LocalTime.of(10, 0), LocalTime.of(12, 0), DATE);
            OptimizerRoom room = new OptimizerRoom(1L, 50, 10L);

            ArgumentCaptor<ScheduleSolution> captor = stubSolverToEchoProblem();
            service.optimize(List.of(a, b), List.of(room), List.of(), 5);

            ScheduleSolution problem = captor.getValue();
            assertThat(findByPlanningId(problem, "a").conflictsWith("b")).isFalse();
            assertThat(findByPlanningId(problem, "b").conflictsWith("a")).isFalse();
        }

        @Test
        @DisplayName("el mismo par que comparte varias fechas no se duplica en la adyacencia")
        void sharedMultipleDatesDoesNotDuplicate() throws Exception {
            OptimizerEvent a = event("a", LocalTime.of(8, 0), LocalTime.of(10, 0), DATE, OTHER_DATE);
            OptimizerEvent b = event("b", LocalTime.of(9, 0), LocalTime.of(11, 0), DATE, OTHER_DATE);
            OptimizerRoom room = new OptimizerRoom(1L, 50, 10L);

            ArgumentCaptor<ScheduleSolution> captor = stubSolverToEchoProblem();
            service.optimize(List.of(a, b), List.of(room), List.of(), 5);

            ScheduleSolution problem = captor.getValue();
            assertThat(findByPlanningId(problem, "a").getConflictingEventIds()).containsExactly("b");
        }

        @Test
        @DisplayName("eventos que solapan en horario pero en fechas distintas no conflictúan")
        void differentDatesDoNotConflict() throws Exception {
            OptimizerEvent a = event("a", LocalTime.of(8, 0), LocalTime.of(10, 0), DATE);
            OptimizerEvent b = event("b", LocalTime.of(9, 0), LocalTime.of(11, 0), OTHER_DATE);
            OptimizerRoom room = new OptimizerRoom(1L, 50, 10L);

            ArgumentCaptor<ScheduleSolution> captor = stubSolverToEchoProblem();
            service.optimize(List.of(a, b), List.of(room), List.of(), 5);

            ScheduleSolution problem = captor.getValue();
            assertThat(findByPlanningId(problem, "a").conflictsWith("b")).isFalse();
            assertThat(findByPlanningId(problem, "b").conflictsWith("a")).isFalse();
        }
    }

    @Nested
    @DisplayName("buildExistingOccupancy (vía optimize)")
    class BuildExistingOccupancy {

        @Test
        @DisplayName("ocupación cuya aula no está entre las candidatas se descarta")
        void occupancyWithNonCandidateRoomIsDiscarded() throws Exception {
            OptimizerRoom candidateRoom = new OptimizerRoom(1L, 50, 10L);
            OptimizerOccupancy occupancy = new OptimizerOccupancy(99L, DATE, LocalTime.of(8, 0), LocalTime.of(10, 0));

            ArgumentCaptor<ScheduleSolution> captor = stubSolverToEchoProblem();
            service.optimize(List.of(), List.of(candidateRoom), List.of(occupancy), 5);

            ScheduleSolution problem = captor.getValue();
            assertThat(problem.getAllocations()).isEmpty();
        }

        @Test
        @DisplayName("ocupaciones duplicadas por planningId (misma aula/fecha/hora) colapsan a una sola")
        void duplicateOccupancyByPlanningIdCollapses() throws Exception {
            OptimizerRoom room = new OptimizerRoom(1L, 50, 10L);
            OptimizerOccupancy occupancyA = new OptimizerOccupancy(1L, DATE, LocalTime.of(8, 0), LocalTime.of(10, 0));
            OptimizerOccupancy occupancyB = new OptimizerOccupancy(1L, DATE, LocalTime.of(8, 0), LocalTime.of(10, 0));

            ArgumentCaptor<ScheduleSolution> captor = stubSolverToEchoProblem();
            service.optimize(List.of(), List.of(room), List.of(occupancyA, occupancyB), 5);

            ScheduleSolution problem = captor.getValue();
            assertThat(problem.getAllocations()).hasSize(1);
        }

        @Test
        @DisplayName("la ocupación existente se modela como allocation pinned con el aula fija como única candidata")
        void occupancyBecomesPinnedAllocation() throws Exception {
            OptimizerRoom room = new OptimizerRoom(1L, 50, 10L);
            OptimizerOccupancy occupancy = new OptimizerOccupancy(1L, DATE, LocalTime.of(8, 0), LocalTime.of(10, 0));

            ArgumentCaptor<ScheduleSolution> captor = stubSolverToEchoProblem();
            service.optimize(List.of(), List.of(room), List.of(occupancy), 5);

            ScheduleSolution problem = captor.getValue();
            assertThat(problem.getAllocations()).hasSize(1);
            ClassAllocation pinnedAllocation = problem.getAllocations().getFirst();
            assertThat(pinnedAllocation.isPinned()).isTrue();
            assertThat(pinnedAllocation.getClassroom()).isEqualTo(room);
            assertThat(pinnedAllocation.getCandidates()).containsExactly(room);
        }
    }

    @Nested
    @DisplayName("toResult (vía optimize)")
    class ToResult {

        @Test
        @DisplayName("las allocations pinned quedan excluidas del resultado")
        void pinnedAllocationsAreExcluded() throws Exception {
            OptimizerRoom room = new OptimizerRoom(1L, 50, 10L);
            OptimizerEvent newEvent = event("nuevo", LocalTime.of(8, 0), LocalTime.of(10, 0), DATE);
            OptimizerOccupancy occupancy = new OptimizerOccupancy(1L, OTHER_DATE, LocalTime.of(8, 0), LocalTime.of(10, 0));

            stubSolverToEchoProblem();
            OptimizationResult result = service.optimize(List.of(newEvent), List.of(room), List.of(occupancy), 5);

            assertThat(result.allocations())
                    .extracting(OptimizerAllocation::eventId)
                    .containsExactly("nuevo");
        }

        @Test
        @DisplayName("el previewId lleva el prefijo prev_")
        void previewIdHasPrefix() throws Exception {
            stubSolverToEchoProblem();
            OptimizationResult result = service.optimize(List.of(), List.of(), List.of(), 5);

            assertThat(result.previewId()).startsWith("prev_");
        }
    }
}
