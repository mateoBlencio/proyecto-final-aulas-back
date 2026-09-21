package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;
import ar.edu.utn.frc.siga.events.dto.request.CreateUniqueEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestOccurrenceResolver")
class RoomRequestOccurrenceResolverTest {

    @Mock
    private AllocationService allocationService;
    @Mock
    private AcademicEventService academicEventService;
    @Mock
    private OccurrenceService occurrenceService;

    @InjectMocks
    private RoomRequestOccurrenceResolver resolver;

    @Test
    @DisplayName("ONE_TIME_ROOM_CHANGE: resuelve la ocurrencia de la fecha del ítem")
    void oneTimeRoomChange() {
        LocalDate date = LocalDate.of(2026, 3, 12);
        RoomRequestItem item = RoomRequestItem.builder().id(1L)
                .request(requestOfType(RoomRequestType.ONE_TIME_ROOM_CHANGE))
                .sourceRecurringEventId(50L).date(date).build();
        when(occurrenceService.findSlotsByEvent(50L, null)).thenReturn(List.of(occurrenceSlot(500L, 50L, date)));

        assertThat(resolver.resolveOccurrencesBySlot(item, 1, false)).containsExactly(List.of(500L));
    }

    @Test
    @DisplayName("PARTIAL_EXAM_IN_CLASS: resuelve la ocurrencia por la fecha del ítem (BUG-01), sin pedir fecha extra")
    void partialExamInClass() {
        LocalDate date = LocalDate.of(2026, 4, 1);
        RoomRequestItem item = RoomRequestItem.builder().id(1L)
                .request(requestOfType(RoomRequestType.PARTIAL_EXAM_IN_CLASS))
                .sourceRecurringEventId(60L).date(date).build();
        when(occurrenceService.findSlotsByEvent(60L, null)).thenReturn(List.of(occurrenceSlot(600L, 60L, date)));

        assertThat(resolver.resolveOccurrencesBySlot(item, 1, false)).containsExactly(List.of(600L));
    }

    @Test
    @DisplayName("ONE_TIME_ROOM_CHANGE: sin ocurrencia para la fecha pedida se rechaza")
    void findOccurrenceOnDate_sinCoincidencia() {
        LocalDate date = LocalDate.of(2026, 3, 12);
        RoomRequestItem item = RoomRequestItem.builder().id(1L)
                .request(requestOfType(RoomRequestType.ONE_TIME_ROOM_CHANGE))
                .sourceRecurringEventId(50L).date(date).build();
        when(occurrenceService.findSlotsByEvent(50L, null)).thenReturn(List.of());

        assertThatThrownBy(() -> resolver.resolveOccurrencesBySlot(item, 1, false))
                .isInstanceOf(InvalidRoomRequestException.class);
    }

    @Test
    @DisplayName("REGULAR_ROOM_CHANGE: resuelve todas las ocurrencias futuras del dayOfWeek del ítem")
    void regularRoomChange() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L)
                .request(requestOfType(RoomRequestType.REGULAR_ROOM_CHANGE))
                .sourceRecurringEventId(70L).dayOfWeek(java.time.DayOfWeek.THURSDAY).build();
        when(occurrenceService.findSlotsByEvent(eq(70L), any(LocalDate.class))).thenReturn(List.of(
                occurrenceSlot(701L, 70L, LocalDate.of(2026, 5, 7)),
                occurrenceSlot(702L, 70L, LocalDate.of(2026, 5, 14)),
                occurrenceSlot(703L, 70L, LocalDate.of(2026, 5, 8))));

        assertThat(resolver.resolveOccurrencesBySlot(item, 1, false))
                .containsExactly(List.of(701L, 702L));
    }

    @Test
    @DisplayName("REGULAR_ROOM_CHANGE: sin ocurrencias futuras se rechaza")
    void regularRoomChange_sinFuturas() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L)
                .request(requestOfType(RoomRequestType.REGULAR_ROOM_CHANGE))
                .sourceRecurringEventId(70L).dayOfWeek(java.time.DayOfWeek.THURSDAY).build();
        when(occurrenceService.findSlotsByEvent(eq(70L), any(LocalDate.class))).thenReturn(List.of());

        assertThatThrownBy(() -> resolver.resolveOccurrencesBySlot(item, 1, false))
                .isInstanceOf(InvalidRoomRequestException.class);
    }

    @Test
    @DisplayName("FINAL_EXAM: primera vez crea el UniqueEvent y resuelve su ocurrencia")
    void finalExam_creaEvento() {
        LocalDate date = LocalDate.of(2026, 7, 1);
        RoomRequestItem item = RoomRequestItem.builder().id(1L)
                .request(requestOfType(RoomRequestType.FINAL_EXAM))
                .date(date).startTime(LocalTime.of(9, 0)).duration(Duration.ofMinutes(90)).estimated(30).build();
        when(academicEventService.createUniqueEvent(any())).thenReturn(uniqueEvent(900L));
        when(academicEventService.findOccurrencesByEventId(900L)).thenReturn(List.of(
                new OccurrenceResponseDto(9000L, 900L, date, OccurrenceStatus.NEEDS_ROOM, LocalTime.of(9, 0), LocalTime.of(10, 30))));

        List<List<Long>> result = resolver.resolveOccurrencesBySlot(item, 1, false);

        assertThat(result).containsExactly(List.of(9000L));
        ArgumentCaptor<CreateUniqueEventRequestDto> captor = ArgumentCaptor.forClass(CreateUniqueEventRequestDto.class);
        verify(academicEventService).createUniqueEvent(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(UniqueEventKind.EXAMEN_FINAL);
    }

    @Test
    @DisplayName("CONFERENCE: primera vez crea el UniqueEvent kind OTRO")
    void conference_creaEventoOtro() {
        LocalDate date = LocalDate.of(2026, 8, 1);
        RoomRequestItem item = RoomRequestItem.builder().id(1L)
                .request(requestOfType(RoomRequestType.CONFERENCE))
                .date(date).startTime(LocalTime.of(18, 0)).duration(Duration.ofMinutes(60)).estimated(80).build();
        when(academicEventService.createUniqueEvent(any())).thenReturn(uniqueEvent(901L));
        when(academicEventService.findOccurrencesByEventId(901L)).thenReturn(List.of(
                new OccurrenceResponseDto(9001L, 901L, date, OccurrenceStatus.NEEDS_ROOM, LocalTime.of(18, 0), LocalTime.of(19, 0))));

        resolver.resolveOccurrencesBySlot(item, 1, false);

        ArgumentCaptor<CreateUniqueEventRequestDto> captor = ArgumentCaptor.forClass(CreateUniqueEventRequestDto.class);
        verify(academicEventService).createUniqueEvent(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(UniqueEventKind.OTRO);
    }

    @Test
    @DisplayName("FINAL_EXAM: reasignar reutiliza la ocurrencia principal existente, sin crear evento nuevo")
    void finalExam_reasignarReutilizaOcurrenciaPrincipal() {
        RoomRequestItemAllocation previous = RoomRequestItemAllocation.builder()
                .occurrenceId(9000L).classroomId(104L).position(1).build();
        RoomRequestItem item = RoomRequestItem.builder().id(1L)
                .request(requestOfType(RoomRequestType.FINAL_EXAM))
                .allocations(new java.util.ArrayList<>(List.of(previous))).build();

        List<List<Long>> result = resolver.resolveOccurrencesBySlot(item, 1, true);

        assertThat(result).containsExactly(List.of(9000L));
        verifyNoInteractions(academicEventService);
    }

    @Test
    @DisplayName("3 aulas: crea ocurrencias 'mirror' simultáneas, una lista por posición")
    void tresAulas_creaOcurrenciasSimultaneas() {
        LocalDate date = LocalDate.of(2026, 7, 1);
        RoomRequestItem item = RoomRequestItem.builder().id(1L)
                .request(requestOfType(RoomRequestType.FINAL_EXAM))
                .date(date).startTime(LocalTime.of(9, 0)).duration(Duration.ofMinutes(90)).estimated(90).build();
        when(academicEventService.createUniqueEvent(any())).thenReturn(uniqueEvent(900L));
        when(academicEventService.findOccurrencesByEventId(900L)).thenReturn(List.of(
                new OccurrenceResponseDto(9000L, 900L, date, OccurrenceStatus.NEEDS_ROOM, LocalTime.of(9, 0), LocalTime.of(10, 30))));
        when(occurrenceService.createSimultaneous(9000L, 2)).thenReturn(List.of(9001L, 9002L));

        List<List<Long>> result = resolver.resolveOccurrencesBySlot(item, 3, false);

        assertThat(result).containsExactly(List.of(9000L), List.of(9001L), List.of(9002L));
    }

    @Test
    @DisplayName("releaseOldMirrors: libera las ocurrencias con orden > 1 y las principales quedan intactas")
    void releaseOldMirrors_liberaSoloMirrors() {
        RoomRequestItemAllocation principal = RoomRequestItemAllocation.builder()
                .occurrenceId(9000L).classroomId(104L).position(1).build();
        RoomRequestItemAllocation mirror1 = RoomRequestItemAllocation.builder()
                .occurrenceId(9001L).classroomId(105L).position(2).build();
        RoomRequestItemAllocation mirror2 = RoomRequestItemAllocation.builder()
                .occurrenceId(9002L).classroomId(106L).position(3).build();
        RoomRequestItem item = RoomRequestItem.builder().id(1L)
                .allocations(List.of(principal, mirror1, mirror2)).build();

        resolver.releaseOldMirrors(item);

        ArgumentCaptor<DeallocationCommand> captor = ArgumentCaptor.forClass(DeallocationCommand.class);
        verify(allocationService).deallocate(captor.capture());
        AllocationTarget.Occurrences target = (AllocationTarget.Occurrences) captor.getValue().targets().getFirst();
        assertThat(target.occurrenceIds()).containsExactlyInAnyOrder(9001L, 9002L);
        verify(occurrenceService).release(9001L);
        verify(occurrenceService).release(9002L);
        verify(occurrenceService, never()).release(9000L);
    }

    @Test
    @DisplayName("releaseOldMirrors: sin mirrors (una sola aula) no llama a deallocate")
    void releaseOldMirrors_sinMirrors() {
        RoomRequestItemAllocation principal = RoomRequestItemAllocation.builder()
                .occurrenceId(9000L).classroomId(104L).position(1).build();
        RoomRequestItem item = RoomRequestItem.builder().id(1L).allocations(List.of(principal)).build();

        resolver.releaseOldMirrors(item);

        verifyNoInteractions(allocationService);
    }

    private static RoomRequest requestOfType(RoomRequestType type) {
        return RoomRequest.builder()
                .type(type)
                .scope(ar.edu.utn.frc.siga.roomrequest.model.AcademicScope.GRADO)
                .teacherName("Ada Lovelace")
                .teacherEmail("ada@frc.utn.edu.ar")
                .teacherPhone("351-1234567")
                .subjectId(1L)
                .build();
    }

    private static OccurrenceSlotDto occurrenceSlot(Long occurrenceId, Long eventId, LocalDate date) {
        return new OccurrenceSlotDto(occurrenceId, eventId, date, LocalTime.of(10, 0), LocalTime.of(11, 0),
                OccurrenceStatus.NEEDS_ROOM, 30);
    }

    private static AcademicEventResponseDto uniqueEvent(Long eventId) {
        return new UniqueEventResponseDto(eventId, EventType.UNIQUE_EVENT, UniqueEventKind.EXAMEN_FINAL, 30,
                LocalTime.of(9, 0), 90L, LocalDate.of(2026, 7, 1), null, null, null);
    }
}
