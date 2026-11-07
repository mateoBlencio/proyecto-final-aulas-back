package ar.edu.utn.frc.siga.sysacad.internal.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.allocation.model.ClassroomMovements.Change;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.SysacadDirectChangeCommand;
import ar.edu.utn.frc.siga.allocation.service.command.SysacadDirectChangeResult;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.sysacad.internal.exception.SysacadConflictException;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadReviewReason;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleStatus;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleRepository;
import ar.edu.utn.frc.siga.sysacad.internal.service.SysacadSyncLock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SysacadReviewService")
class SysacadReviewServiceTest {

    private static final long ID = 7L;
    private static final String USER = "sub@frc.utn.edu.ar";

    @Mock
    private SysacadScheduleRepository scheduleRepository;
    @Mock
    private SysacadScheduleStateWriter stateWriter;
    @Mock
    private SysacadSyncLock syncLock;
    @Mock
    private ClassroomService classroomService;
    @Mock
    private AllocationService allocationService;

    private SysacadReviewService service;
    private SysacadSchedule schedule;

    @BeforeEach
    void setUp() {
        service = new SysacadReviewService(scheduleRepository, stateWriter, syncLock, classroomService,
                allocationService);
        schedule = SysacadSchedule.builder().id(ID).year(2026).specialty(5).plan(2008).subject(115).commission(7)
                .dayOfWeek(1).semester(1).courseCode("1K1").knownBuilding(5).knownRoom(228)
                .differentBuilding(5).differentRoom(229)
                .status(SysacadScheduleStatus.REVIEW).reviewReason(SysacadReviewReason.ROOM_OCCUPIED)
                .eventId1(10L).eventId2(11L).build();
    }

    private void lockFree() {
        when(syncLock.tryLock()).thenReturn(true);
    }

    private void scheduleExists() {
        when(scheduleRepository.findById(ID)).thenReturn(Optional.of(schedule));
    }

    private static ClassroomResponseDto classroom(long id, int room) {
        return new ClassroomResponseDto(id, room, 40, 1L, "Central", 1L, "Normal", null, true);
    }

    private SysacadDirectChangeCommand appliedCommand() {
        ArgumentCaptor<SysacadDirectChangeCommand> captor = ArgumentCaptor.forClass(SysacadDirectChangeCommand.class);
        verify(allocationService).applySysacadDirectChange(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("resendSiga delega en el state writer con el usuario y suelta el lock")
    void resendSigaDelegatesAndReleasesLock() {
        lockFree();
        scheduleExists();

        service.resendSiga(ID, USER);

        InOrder order = inOrder(syncLock, stateWriter);
        order.verify(syncLock).tryLock();
        order.verify(stateWriter).resolveResendSiga(ID, USER);
        order.verify(syncLock).unlock();
        verifyNoInteractions(allocationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SYNCED", "PENDING", "SENDING", "WAITING"})
    @DisplayName("resendSiga y keepSysacad rechazan con 409 un horario que no está en REVIEW y sueltan el lock")
    void rejectsScheduleOutsideReview(String status) {
        lockFree();
        schedule.setStatus(SysacadScheduleStatus.valueOf(status));
        scheduleExists();

        assertThatThrownBy(() -> service.resendSiga(ID, USER)).isInstanceOf(SysacadConflictException.class);
        assertThatThrownBy(() -> service.keepSysacad(ID, USER, "obs")).isInstanceOf(SysacadConflictException.class);

        verify(stateWriter, never()).resolveResendSiga(any(), any());
        verify(stateWriter, never()).resolveKeepSysacad(any(), any(), any());
        verifyNoInteractions(allocationService);
        verify(syncLock, org.mockito.Mockito.times(2)).unlock();
    }

    @Test
    @DisplayName("con el lock tomado responde 409, no toca nada y no suelta un lock ajeno")
    void rejectsWhenLockIsTaken() {
        when(syncLock.tryLock()).thenReturn(false);

        assertThatThrownBy(() -> service.resendSiga(ID, USER)).isInstanceOf(SysacadConflictException.class);
        assertThatThrownBy(() -> service.keepSysacad(ID, USER, "obs")).isInstanceOf(SysacadConflictException.class);

        verify(syncLock, never()).unlock();
        verifyNoInteractions(scheduleRepository, stateWriter, allocationService);
    }

    @Test
    @DisplayName("horario inexistente: 404 y suelta el lock")
    void unknownScheduleIs404AndReleasesLock() {
        lockFree();
        when(scheduleRepository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resendSiga(ID, USER)).isInstanceOf(ResourceNotFoundException.class);

        verify(syncLock).unlock();
    }

    @Test
    @DisplayName("si falla la operación suelta el lock igual y propaga el error")
    void releasesLockWhenOperationFails() {
        lockFree();
        scheduleExists();
        doThrow(new IllegalStateException("base caída")).when(stateWriter).resolveResendSiga(ID, USER);

        assertThatThrownBy(() -> service.resendSiga(ID, USER)).isInstanceOf(IllegalStateException.class);

        verify(syncLock).unlock();
    }

    @Test
    @DisplayName("keepSysacad con aula real: llama a allocation con applyManualRules, el actor del usuario y la observación")
    void keepSysacadWithRealRoomAppliesManualRules() {
        lockFree();
        scheduleExists();
        when(classroomService.findByRoomNumberAndBuildingCode(229, 5)).thenReturn(Optional.of(classroom(99L, 229)));
        when(allocationService.applySysacadDirectChange(any())).thenReturn(new SysacadDirectChangeResult.Applied(
                List.of(new Change(1L, 10L, LocalDate.of(2026, 4, 6), LocalTime.of(8, 0), LocalTime.of(9, 30), 50L,
                        99L),
                        new Change(2L, 11L, LocalDate.of(2026, 4, 13), LocalTime.of(8, 0), LocalTime.of(9, 30), null,
                                99L))));

        service.keepSysacad(ID, USER, "Lo pidió Subsecretaría");

        SysacadDirectChangeCommand command = appliedCommand();
        assertThat(command.applyManualRules()).isTrue();
        assertThat(command.actor()).isEqualTo(USER);
        assertThat(command.reason()).isEqualTo("Lo pidió Subsecretaría");
        assertThat(command.classroomId()).isEqualTo(99L);
        assertThat(command.eventIds()).containsExactlyInAnyOrder(10L, 11L);
        ArgumentCaptor<List<AffectedOccurrence>> annulled = ArgumentCaptor.forClass(List.class);
        verify(stateWriter).resolveKeepSysacad(org.mockito.ArgumentMatchers.eq(ID),
                org.mockito.ArgumentMatchers.eq(USER), annulled.capture());
        assertThat(annulled.getValue()).extracting(AffectedOccurrence::occurrenceId).containsExactly(1L);
        verify(syncLock).unlock();
    }

    @ParameterizedTest
    @ValueSource(ints = {999, 0})
    @DisplayName("keepSysacad con 999 o 0: llama con classroomId nulo y no busca aula")
    void keepSysacadWithSentinelRoomPassesNullClassroom(int room) {
        lockFree();
        schedule.setDifferentRoom(room);
        scheduleExists();
        when(allocationService.applySysacadDirectChange(any()))
                .thenReturn(new SysacadDirectChangeResult.Applied(List.of()));

        service.keepSysacad(ID, USER, "obs");

        assertThat(appliedCommand().classroomId()).isNull();
        verifyNoInteractions(classroomService);
        verify(stateWriter).resolveKeepSysacad(ID, USER, List.of());
    }

    @Test
    @DisplayName("keepSysacad sin el aula en SIGA: 409, no escribe nada y suelta el lock")
    void keepSysacadWithUnknownRoomIs409() {
        lockFree();
        scheduleExists();
        when(classroomService.findByRoomNumberAndBuildingCode(229, 5)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.keepSysacad(ID, USER, "obs")).isInstanceOf(SysacadConflictException.class);

        verifyNoInteractions(allocationService);
        verify(stateWriter, never()).resolveKeepSysacad(any(), any(), any());
        verify(syncLock).unlock();
    }

    @Test
    @DisplayName("keepSysacad sin lectura distinta usa el valor conocido como última lectura")
    void keepSysacadWithoutDifferenceUsesKnownValue() {
        lockFree();
        schedule.setDifferentBuilding(null);
        schedule.setDifferentRoom(null);
        scheduleExists();
        when(classroomService.findByRoomNumberAndBuildingCode(228, 5)).thenReturn(Optional.of(classroom(50L, 228)));
        when(allocationService.applySysacadDirectChange(any()))
                .thenReturn(new SysacadDirectChangeResult.Applied(List.of()));

        service.keepSysacad(ID, USER, "obs");

        assertThat(appliedCommand().classroomId()).isEqualTo(50L);
    }

    @Test
    @DisplayName("keepSysacad con aula ocupada devuelta pese a applyManualRules es un error interno, sin cerrar la revisión")
    void keepSysacadOccupiedResultIsInternalError() {
        lockFree();
        scheduleExists();
        when(classroomService.findByRoomNumberAndBuildingCode(229, 5)).thenReturn(Optional.of(classroom(99L, 229)));
        when(allocationService.applySysacadDirectChange(any()))
                .thenReturn(new SysacadDirectChangeResult.Occupied(List.of(1L)));

        assertThatThrownBy(() -> service.keepSysacad(ID, USER, "obs")).isInstanceOf(IllegalStateException.class);

        verify(stateWriter, never()).resolveKeepSysacad(any(), any(), any());
        verify(syncLock).unlock();
    }

    @Test
    @DisplayName("el error de allocation (aula ocupada con solapamiento bloqueante) se propaga sin cerrar la revisión")
    void allocationErrorPropagatesWithoutResolving() {
        lockFree();
        scheduleExists();
        when(classroomService.findByRoomNumberAndBuildingCode(229, 5)).thenReturn(Optional.of(classroom(99L, 229)));
        when(allocationService.applySysacadDirectChange(any())).thenThrow(new IllegalArgumentException("solapa"));

        assertThatThrownBy(() -> service.keepSysacad(ID, USER, "obs")).isInstanceOf(IllegalArgumentException.class);

        verify(stateWriter, never()).resolveKeepSysacad(any(), any(), any());
        verify(syncLock).unlock();
    }

    @Test
    @DisplayName("sin eventos vinculados no llama a allocation y cierra la revisión igual")
    void keepSysacadWithoutEventsSkipsAllocation() {
        lockFree();
        schedule.setEventId1(null);
        schedule.setEventId2(null);
        scheduleExists();
        when(classroomService.findByRoomNumberAndBuildingCode(229, 5)).thenReturn(Optional.of(classroom(99L, 229)));

        service.keepSysacad(ID, USER, "obs");

        verifyNoInteractions(allocationService);
        verify(stateWriter).resolveKeepSysacad(ID, USER, List.of());
    }
}
