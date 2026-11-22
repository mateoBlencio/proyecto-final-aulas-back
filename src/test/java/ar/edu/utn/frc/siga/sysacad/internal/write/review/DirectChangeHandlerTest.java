package ar.edu.utn.frc.siga.sysacad.internal.write.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.allocation.model.ClassroomMovements.Change;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.SysacadDirectChangeCommand;
import ar.edu.utn.frc.siga.allocation.service.command.SysacadDirectChangeResult;
import ar.edu.utn.frc.siga.common.security.SystemScope;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.roomrequest.dto.response.OccurrenceRequestStateDto;
import ar.edu.utn.frc.siga.roomrequest.model.OccurrenceRequestState;
import ar.edu.utn.frc.siga.roomrequest.service.OccurrenceRequestQuery;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadReviewReason;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleStatus;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ar.edu.utn.frc.siga.sysacad.internal.write.plan.AffectedOccurrence;
import ar.edu.utn.frc.siga.sysacad.internal.write.state.SysacadScheduleStateWriter;

@ExtendWith(MockitoExtension.class)
@DisplayName("DirectChangeHandler")
class DirectChangeHandlerTest {

    private static final long SCHEDULE_ID = 1L;
    private static final long CLASSROOM_ID = 50L;
    private static final LocalDate FUTURE = LocalDate.of(2099, 3, 2);
    private static final LocalDate PAST = LocalDate.of(2000, 3, 2);

    @Mock
    private SysacadScheduleRepository scheduleRepository;
    @Mock
    private SysacadScheduleStateWriter stateWriter;
    @Mock
    private ClassroomService classroomService;
    @Mock
    private OccurrenceService occurrenceService;
    @Mock
    private AllocationService allocationService;
    @Mock
    private OccurrenceRequestQuery occurrenceRequestQuery;

    private DirectChangeHandler handler;
    private SysacadSchedule schedule;

    @BeforeEach
    void setUp() {
        handler = new DirectChangeHandler(scheduleRepository, stateWriter, classroomService, occurrenceService,
                allocationService, occurrenceRequestQuery);
        schedule = SysacadSchedule.builder().id(SCHEDULE_ID).year(2026).specialty(5).plan(2008).subject(115)
                .commission(7).dayOfWeek(1).semester(1).knownBuilding(5).knownRoom(228)
                .differentBuilding(5).differentRoom(229).eventId1(10L).eventId2(11L)
                .status(SysacadScheduleStatus.PENDING).build();
        lenient().when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
    }

    private static OccurrenceSlotDto slot(long occurrenceId, long eventId, LocalDate date,
            OccurrenceStatus status, Long mirrorOf) {
        return new OccurrenceSlotDto(occurrenceId, eventId, date, LocalTime.of(8, 0), LocalTime.of(10, 15), status,
                30, 1, null, null, null, mirrorOf);
    }

    private static OccurrenceSlotDto slot(long occurrenceId, LocalDate date) {
        return slot(occurrenceId, 10L, date, OccurrenceStatus.NEEDS_ROOM, null);
    }

    private void knownClassroom() {
        when(classroomService.findByRoomNumberAndBuildingCode(229, 5)).thenReturn(Optional.of(
                new ClassroomResponseDto(CLASSROOM_ID, 229, 40, 5L, "Edificio", 1L, "Normal", null, true)));
    }

    private void slotsAre(OccurrenceSlotDto... slots) {
        when(occurrenceService.findSlotsByEvents(any(), any(LocalDate.class))).thenReturn(List.of(slots));
    }

    private void noRequests() {
        when(occurrenceRequestQuery.findStates(anyCollection())).thenReturn(Map.of());
    }

    @Test
    @DisplayName("Un horario que no existe no hace nada")
    void unknownScheduleDoesNothing() {
        when(scheduleRepository.findById(99L)).thenReturn(Optional.empty());

        handler.handle(99L);

        verifyNoInteractions(stateWriter, allocationService, classroomService);
    }

    @Test
    @DisplayName("Un horario en REVIEW no hace nada")
    void scheduleInReviewDoesNothing() {
        schedule.setStatus(SysacadScheduleStatus.REVIEW);

        handler.handle(SCHEDULE_ID);

        verifyNoInteractions(stateWriter, allocationService, classroomService);
    }

    @Test
    @DisplayName("Un horario sin diferencia registrada no hace nada")
    void scheduleWithoutDifferenceDoesNothing() {
        schedule.setDifferentBuilding(null);
        schedule.setDifferentRoom(null);

        handler.handle(SCHEDULE_ID);

        verifyNoInteractions(stateWriter, allocationService, classroomService);
    }

    @ParameterizedTest(name = "aula {0}")
    @ValueSource(ints = { 999, 0 })
    @DisplayName("Aula centinela en SysAcad (999 o 0): revisión NO_ROOM_IN_SYSACAD sin anular nada")
    void sentinelRoomOpensReview(int room) {
        schedule.setDifferentRoom(room);

        handler.handle(SCHEDULE_ID);

        verify(stateWriter).toReview(SCHEDULE_ID, SysacadReviewReason.NO_ROOM_IN_SYSACAD, List.of());
        verifyNoInteractions(allocationService, classroomService);
    }

    @Test
    @DisplayName("Aula desconocida en SIGA: revisión ROOM_UNKNOWN_IN_SIGA sin anular nada")
    void unknownClassroomOpensReview() {
        when(classroomService.findByRoomNumberAndBuildingCode(229, 5)).thenReturn(Optional.empty());

        handler.handle(SCHEDULE_ID);

        verify(stateWriter).toReview(SCHEDULE_ID, SysacadReviewReason.ROOM_UNKNOWN_IN_SIGA, List.of());
        verifyNoInteractions(allocationService);
    }

    @Test
    @DisplayName("Una clase futura con el aula liberada: revisión RELEASED_FUTURE_CLASS sin anular nada")
    void releasedFutureClassOpensReview() {
        knownClassroom();
        slotsAre(slot(1, 10L, FUTURE, OccurrenceStatus.ROOM_RELEASED, null), slot(2, FUTURE));

        handler.handle(SCHEDULE_ID);

        verify(stateWriter).toReview(SCHEDULE_ID, SysacadReviewReason.RELEASED_FUTURE_CLASS, List.of());
        verify(allocationService, never()).applySysacadDirectChange(any());
        verifyNoInteractions(occurrenceRequestQuery);
    }

    @Test
    @DisplayName("Una clase liberada pasada o espejo no frena el cambio directo")
    void releasedPastOrMirrorClassDoesNotBlock() {
        knownClassroom();
        slotsAre(slot(1, 10L, PAST, OccurrenceStatus.ROOM_RELEASED, null),
                slot(2, 10L, FUTURE, OccurrenceStatus.ROOM_RELEASED, 3L), slot(4, FUTURE));
        noRequests();
        when(allocationService.applySysacadDirectChange(any()))
                .thenReturn(new SysacadDirectChangeResult.Applied(List.of()));

        handler.handle(SCHEDULE_ID);

        verify(allocationService).applySysacadDirectChange(any());
        verify(stateWriter, never()).toReview(anyLong(), any(), anyCollection());
    }

    @Test
    @DisplayName("Un pedido PENDING sobre una clase futura: revisión REQUEST_ON_FUTURE_CLASS con las ocurrencias ordenadas")
    void pendingRequestOpensReview() {
        knownClassroom();
        slotsAre(slot(3, FUTURE), slot(2, FUTURE));
        when(occurrenceRequestQuery.findStates(anyCollection())).thenReturn(Map.of(
                3L, new OccurrenceRequestStateDto(3L, 70L, OccurrenceRequestState.PENDING),
                2L, new OccurrenceRequestStateDto(2L, 71L, OccurrenceRequestState.PENDING)));

        handler.handle(SCHEDULE_ID);

        verify(stateWriter).toReview(SCHEDULE_ID, SysacadReviewReason.REQUEST_ON_FUTURE_CLASS, List.of(2L, 3L));
        verify(allocationService, never()).applySysacadDirectChange(any());
    }

    @Test
    @DisplayName("Un pedido ACCEPTED sobre una clase futura: revisión REQUEST_ON_FUTURE_CLASS sin anular nada")
    void acceptedRequestOpensReview() {
        knownClassroom();
        slotsAre(slot(2, FUTURE));
        when(occurrenceRequestQuery.findStates(anyCollection())).thenReturn(Map.of(
                2L, new OccurrenceRequestStateDto(2L, 71L, OccurrenceRequestState.ACCEPTED)));

        handler.handle(SCHEDULE_ID);

        verify(stateWriter).toReview(SCHEDULE_ID, SysacadReviewReason.REQUEST_ON_FUTURE_CLASS, List.of(2L));
        verify(allocationService, never()).applySysacadDirectChange(any());
    }

    @Test
    @DisplayName("Un pedido CANCELLED no frena el cambio directo")
    void cancelledRequestDoesNotBlock() {
        knownClassroom();
        slotsAre(slot(2, FUTURE));
        when(occurrenceRequestQuery.findStates(anyCollection())).thenReturn(Map.of(
                2L, new OccurrenceRequestStateDto(2L, 71L, OccurrenceRequestState.CANCELLED)));
        when(allocationService.applySysacadDirectChange(any()))
                .thenReturn(new SysacadDirectChangeResult.Applied(List.of()));

        handler.handle(SCHEDULE_ID);

        verify(allocationService).applySysacadDirectChange(any());
    }

    @Test
    @DisplayName("Un pedido sobre una clase pasada no frena el cambio directo")
    void requestOnPastClassDoesNotBlock() {
        knownClassroom();
        slotsAre(slot(1, PAST), slot(2, FUTURE));
        when(occurrenceRequestQuery.findStates(anyCollection())).thenAnswer(invocation -> {
            java.util.Collection<Long> ids = invocation.getArgument(0);
            assertThat(ids).containsExactly(2L);
            return Map.of();
        });
        when(allocationService.applySysacadDirectChange(any()))
                .thenReturn(new SysacadDirectChangeResult.Applied(List.of()));

        handler.handle(SCHEDULE_ID);

        verify(allocationService).applySysacadDirectChange(any());
    }

    @Test
    @DisplayName("Aula ocupada: revisión ROOM_OCCUPIED con las ocurrencias en conflicto")
    void occupiedRoomOpensReview() {
        knownClassroom();
        slotsAre(slot(2, FUTURE));
        noRequests();
        when(allocationService.applySysacadDirectChange(any()))
                .thenReturn(new SysacadDirectChangeResult.Occupied(List.of(2L)));

        handler.handle(SCHEDULE_ID);

        verify(stateWriter).toReview(SCHEDULE_ID, SysacadReviewReason.ROOM_OCCUPIED, List.of(2L));
        verify(stateWriter, never()).recordDirectChangeApplied(anyLong(), any());
    }

    @Test
    @DisplayName("Applied registra el cambio con las clases que tenían aula, y llama a la anulación con ambos eventos bajo SystemScope")
    void appliedRecordsAffectedClasses() {
        knownClassroom();
        slotsAre(slot(2, FUTURE), slot(3, FUTURE));
        noRequests();
        AtomicBoolean systemScopeActive = new AtomicBoolean();
        when(allocationService.applySysacadDirectChange(any())).thenAnswer(invocation -> {
            systemScopeActive.set(SystemScope.isActive());
            return new SysacadDirectChangeResult.Applied(List.of(
                    new Change(2L, 10L, FUTURE, LocalTime.of(8, 0), LocalTime.of(10, 15), 40L, CLASSROOM_ID),
                    new Change(3L, 10L, FUTURE, LocalTime.of(8, 0), LocalTime.of(10, 15), null, CLASSROOM_ID)));
        });

        handler.handle(SCHEDULE_ID);

        ArgumentCaptor<SysacadDirectChangeCommand> command = ArgumentCaptor.forClass(SysacadDirectChangeCommand.class);
        verify(allocationService).applySysacadDirectChange(command.capture());
        assertThat(command.getValue().eventIds()).containsExactlyInAnyOrder(10L, 11L);
        assertThat(command.getValue().classroomId()).isEqualTo(CLASSROOM_ID);
        assertThat(command.getValue().reason()).isEqualTo("Cambio directo en SysAcad");
        assertThat(systemScopeActive).isTrue();
        verify(stateWriter).recordDirectChangeApplied(SCHEDULE_ID,
                List.of(new AffectedOccurrence(2L, FUTURE, 40L)));
        verify(stateWriter, never()).toReview(anyLong(), any(), anyCollection());
    }
}
