package ar.edu.utn.frc.siga.solver.service.impl;

import ar.edu.utn.frc.siga.solver.config.SolverProperties;
import ar.edu.utn.frc.siga.solver.exception.ExpiredPreviewException;
import ar.edu.utn.frc.siga.solver.model.ClassAllocation;
import ar.edu.utn.frc.siga.solver.model.ScheduleSolution;
import ar.edu.utn.frc.siga.solver.model.SolverAllocation;
import ar.edu.utn.frc.siga.solver.model.SolverEvent;
import ar.edu.utn.frc.siga.solver.model.SolverOccupancy;
import ar.edu.utn.frc.siga.solver.model.SolverPreview;
import ar.edu.utn.frc.siga.solver.model.SolverRoom;
import ar.edu.utn.frc.siga.solver.service.PreviewStore;
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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SolverServiceImpl")
class SolverServiceImplTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 3);
    private static final LocalDate OTHER_DATE = LocalDate.of(2026, 8, 4);

    @Mock
    private SolverManager<ScheduleSolution> solverManager;

    @Mock
    private PreviewStore previewStore;

    private SolverServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SolverServiceImpl(solverManager, new SolverProperties(), previewStore);
    }

    private static SolverEvent event(String id, LocalTime start, LocalTime end, LocalDate... dates) {
        return new SolverEvent(id, null, 10, start, end, Set.of(dates));
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
    @DisplayName("computeConflicts (vía preview)")
    class ComputeConflicts {

        @Test
        @DisplayName("eventos que solapan en la misma fecha generan un par simétrico")
        void overlappingEventsAreSymmetricallyConflicting() throws Exception {
            SolverEvent a = event("a", LocalTime.of(8, 0), LocalTime.of(10, 0), DATE);
            SolverEvent b = event("b", LocalTime.of(9, 0), LocalTime.of(11, 0), DATE);
            SolverRoom room = new SolverRoom(1, 50, 10);

            ArgumentCaptor<ScheduleSolution> captor = stubSolverToEchoProblem();
            service.preview(List.of(a, b), List.of(room), List.of(), 5);

            ScheduleSolution problem = captor.getValue();
            assertThat(findByPlanningId(problem, "a").conflictsWith("b")).isTrue();
            assertThat(findByPlanningId(problem, "b").conflictsWith("a")).isTrue();
        }

        @Test
        @DisplayName("eventos contiguos (fin de uno == inicio del otro) no conflictúan")
        void contiguousEventsDoNotConflict() throws Exception {
            SolverEvent a = event("a", LocalTime.of(8, 0), LocalTime.of(10, 0), DATE);
            SolverEvent b = event("b", LocalTime.of(10, 0), LocalTime.of(12, 0), DATE);
            SolverRoom room = new SolverRoom(1, 50, 10);

            ArgumentCaptor<ScheduleSolution> captor = stubSolverToEchoProblem();
            service.preview(List.of(a, b), List.of(room), List.of(), 5);

            ScheduleSolution problem = captor.getValue();
            assertThat(findByPlanningId(problem, "a").conflictsWith("b")).isFalse();
            assertThat(findByPlanningId(problem, "b").conflictsWith("a")).isFalse();
        }

        @Test
        @DisplayName("el mismo par que comparte varias fechas no se duplica en la adyacencia")
        void sharedMultipleDatesDoesNotDuplicate() throws Exception {
            SolverEvent a = event("a", LocalTime.of(8, 0), LocalTime.of(10, 0), DATE, OTHER_DATE);
            SolverEvent b = event("b", LocalTime.of(9, 0), LocalTime.of(11, 0), DATE, OTHER_DATE);
            SolverRoom room = new SolverRoom(1, 50, 10);

            ArgumentCaptor<ScheduleSolution> captor = stubSolverToEchoProblem();
            service.preview(List.of(a, b), List.of(room), List.of(), 5);

            ScheduleSolution problem = captor.getValue();
            assertThat(findByPlanningId(problem, "a").getConflictingEventIds()).containsExactly("b");
        }

        @Test
        @DisplayName("eventos que solapan en horario pero en fechas distintas no conflictúan")
        void differentDatesDoNotConflict() throws Exception {
            SolverEvent a = event("a", LocalTime.of(8, 0), LocalTime.of(10, 0), DATE);
            SolverEvent b = event("b", LocalTime.of(9, 0), LocalTime.of(11, 0), OTHER_DATE);
            SolverRoom room = new SolverRoom(1, 50, 10);

            ArgumentCaptor<ScheduleSolution> captor = stubSolverToEchoProblem();
            service.preview(List.of(a, b), List.of(room), List.of(), 5);

            ScheduleSolution problem = captor.getValue();
            assertThat(findByPlanningId(problem, "a").conflictsWith("b")).isFalse();
            assertThat(findByPlanningId(problem, "b").conflictsWith("a")).isFalse();
        }
    }

    @Nested
    @DisplayName("buildExistingOccupancy (vía preview)")
    class BuildExistingOccupancy {

        @Test
        @DisplayName("ocupación cuya aula no está entre las candidatas se descarta")
        void occupancyWithNonCandidateRoomIsDiscarded() throws Exception {
            SolverRoom candidateRoom = new SolverRoom(1, 50, 10);
            SolverOccupancy occupancy = new SolverOccupancy(99, DATE, LocalTime.of(8, 0), LocalTime.of(10, 0));

            ArgumentCaptor<ScheduleSolution> captor = stubSolverToEchoProblem();
            service.preview(List.of(), List.of(candidateRoom), List.of(occupancy), 5);

            ScheduleSolution problem = captor.getValue();
            assertThat(problem.getAllocations()).isEmpty();
        }

        @Test
        @DisplayName("ocupaciones duplicadas por planningId (misma aula/fecha/hora) colapsan a una sola")
        void duplicateOccupancyByPlanningIdCollapses() throws Exception {
            SolverRoom room = new SolverRoom(1, 50, 10);
            SolverOccupancy occupancyA = new SolverOccupancy(1, DATE, LocalTime.of(8, 0), LocalTime.of(10, 0));
            SolverOccupancy occupancyB = new SolverOccupancy(1, DATE, LocalTime.of(8, 0), LocalTime.of(10, 0));

            ArgumentCaptor<ScheduleSolution> captor = stubSolverToEchoProblem();
            service.preview(List.of(), List.of(room), List.of(occupancyA, occupancyB), 5);

            ScheduleSolution problem = captor.getValue();
            assertThat(problem.getAllocations()).hasSize(1);
        }

        @Test
        @DisplayName("la ocupación existente se modela como allocation pinned con el aula fija como única candidata")
        void occupancyBecomesPinnedAllocation() throws Exception {
            SolverRoom room = new SolverRoom(1, 50, 10);
            SolverOccupancy occupancy = new SolverOccupancy(1, DATE, LocalTime.of(8, 0), LocalTime.of(10, 0));

            ArgumentCaptor<ScheduleSolution> captor = stubSolverToEchoProblem();
            service.preview(List.of(), List.of(room), List.of(occupancy), 5);

            ScheduleSolution problem = captor.getValue();
            assertThat(problem.getAllocations()).hasSize(1);
            ClassAllocation pinnedAllocation = problem.getAllocations().get(0);
            assertThat(pinnedAllocation.isPinned()).isTrue();
            assertThat(pinnedAllocation.getClassroom()).isEqualTo(room);
            assertThat(pinnedAllocation.getCandidates()).containsExactly(room);
        }
    }

    @Nested
    @DisplayName("toPreview (vía preview)")
    class ToPreview {

        @Test
        @DisplayName("las allocations pinned quedan excluidas del resultado")
        void pinnedAllocationsAreExcluded() throws Exception {
            SolverRoom room = new SolverRoom(1, 50, 10);
            SolverEvent newEvent = event("nuevo", LocalTime.of(8, 0), LocalTime.of(10, 0), DATE);
            SolverOccupancy occupancy = new SolverOccupancy(1, OTHER_DATE, LocalTime.of(8, 0), LocalTime.of(10, 0));

            stubSolverToEchoProblem();
            SolverPreview preview = service.preview(List.of(newEvent), List.of(room), List.of(occupancy), 5);

            assertThat(preview.allocations())
                    .extracting(SolverAllocation::eventId)
                    .containsExactly("nuevo");
        }

        @Test
        @DisplayName("el previewId lleva el prefijo prev_")
        void previewIdHasPrefix() throws Exception {
            stubSolverToEchoProblem();
            SolverPreview preview = service.preview(List.of(), List.of(), List.of(), 5);

            assertThat(preview.previewId()).startsWith("prev_");
        }

        @Test
        @DisplayName("la preview generada se guarda en el PreviewStore")
        void previewIsSaved() throws Exception {
            stubSolverToEchoProblem();
            SolverPreview preview = service.preview(List.of(), List.of(), List.of(), 5);

            verify(previewStore).save(preview);
        }
    }

    @Nested
    @DisplayName("getPreview / invalidatePreview")
    class PreviewLifecycle {

        @Test
        @DisplayName("getPreview de un id inexistente lanza ExpiredPreviewException")
        void getPreviewMissingThrows() {
            when(previewStore.get("prev_missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPreview("prev_missing"))
                    .isInstanceOf(ExpiredPreviewException.class);
        }

        @Test
        @DisplayName("getPreview delega en el PreviewStore cuando existe")
        void getPreviewDelegatesToStore() {
            SolverPreview preview = new SolverPreview("prev_x", List.of());
            when(previewStore.get("prev_x")).thenReturn(Optional.of(preview));

            assertThat(service.getPreview("prev_x")).isEqualTo(preview);
        }

        @Test
        @DisplayName("invalidatePreview delega en PreviewStore.remove")
        void invalidatePreviewDelegatesToRemove() {
            service.invalidatePreview("prev_x");

            verify(previewStore).remove("prev_x");
        }
    }
}
