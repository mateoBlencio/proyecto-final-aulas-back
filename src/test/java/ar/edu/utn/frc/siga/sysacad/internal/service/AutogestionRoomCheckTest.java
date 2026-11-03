package ar.edu.utn.frc.siga.sysacad.internal.service;

import ar.edu.utn.frc.siga.academic.dto.SubjectFilter;
import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceResponseDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.sysacad.dto.request.SendCommissionMessageRequest;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.model.AutogestionNotifiedRoom;
import ar.edu.utn.frc.siga.sysacad.internal.repository.AutogestionNotifiedRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AutogestionRoomCheck: chequeo de fin de día de los cambios de aula")
class AutogestionRoomCheckTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);
    private static final LocalTime START = LocalTime.of(13, 15);

    private SysacadCommissionMessageService messageService;
    private AllocationService allocationService;
    private AutogestionNotifiedRoomRepository repository;
    private AutogestionRoomCheck check;
    private final List<AutogestionNotifiedRoom> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        messageService = mock(SysacadCommissionMessageService.class);
        allocationService = mock(AllocationService.class);
        repository = mock(AutogestionNotifiedRoomRepository.class);
        CommissionService commissionService = mock(CommissionService.class);
        SubjectService subjectService = mock(SubjectService.class);
        AcademicEventService eventService = mock(AcademicEventService.class);
        ClassroomService classroomService = mock(ClassroomService.class);
        SysacadProperties properties = new SysacadProperties();
        properties.setAutogestion(new SysacadProperties.Autogestion(true, "TEST", 5, 581, 100, "U", 14, "0 0 23 * * *"));

        CommissionResponseDto commission = new CommissionResponseDto(1L, "TEST",
                new AcademicPeriodResponseDto(1L, 2026, 0, TODAY, TODAY, null, null), null);
        when(commissionService.findActiveByCourseCode("TEST")).thenReturn(commission);
        SubjectResponseDto subject = new SubjectResponseDto(7L, 581, "Materia", null, null, true, null);
        when(subjectService.findAll(any(SubjectFilter.class), any(), org.mockito.ArgumentMatchers.eq(false)))
                .thenReturn(new PageImpl<>(List.of(subject)));
        when(eventService.findClassOccurrences(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(List.of(
                occurrence(1, TODAY.plusDays(1)), occurrence(2, TODAY.plusDays(7)), occurrence(3, TODAY.plusDays(8))));
        when(classroomService.findByIdsIncludingDeactivated(any())).thenReturn(List.of(
                classroom(10, 220), classroom(11, 305)));
        when(repository.findByOccurrenceIdIn(any())).thenAnswer(call -> stored.stream()
                .filter(row -> ((java.util.Collection<?>) call.getArgument(0)).contains(row.getOccurrenceId()))
                .toList());
        when(repository.count()).thenAnswer(call -> (long) stored.size());
        when(repository.saveAll(any())).thenAnswer(call -> {
            Iterable<AutogestionNotifiedRoom> rows = call.getArgument(0);
            for (AutogestionNotifiedRoom row : rows) {
                stored.removeIf(existing -> existing.getOccurrenceId().equals(row.getOccurrenceId()));
                stored.add(row);
            }
            return List.of();
        });
        check = new AutogestionRoomCheck(messageService, properties, commissionService, subjectService, eventService,
                allocationService, classroomService, repository);
    }

    private static OccurrenceResponseDto occurrence(long id, LocalDate date) {
        return new OccurrenceResponseDto(id, 1L, date, OccurrenceStatus.NEEDS_ROOM, START, START.plusHours(4), null,
                null, null);
    }

    private static ClassroomResponseDto classroom(long id, int number) {
        return new ClassroomResponseDto(id, number, 40, 1L, "Edif", 1L, "Común", null, true, false, false, false);
    }

    private void allocated(Map<Long, Long> classroomByOccurrence) {
        when(allocationService.findByOccurrenceIds(any())).thenReturn(classroomByOccurrence.entrySet().stream()
                .map(entry -> new AllocationResponseDto(null, null, Instant.now(), null,
                        occurrence(entry.getKey(), TODAY), null, classroom(entry.getValue(), 0), null))
                .collect(Collectors.toList()));
    }

    private Map<Long, Long> storedRooms() {
        Map<Long, Long> rooms = new java.util.HashMap<>();
        stored.forEach(row -> rooms.put(row.getOccurrenceId(), row.getClassroomId()));
        return rooms;
    }

    @Test
    @DisplayName("la primera corrida solo guarda la foto de aulas y no envía nada")
    void firstRunOnlyRecordsBaseline() {
        allocated(Map.of(1L, 10L, 2L, 10L));

        check.check(TODAY);

        verify(messageService, never()).send(any());
        assertThat(storedRooms()).containsOnlyKeys(1L, 2L, 3L).containsEntry(1L, 10L).containsEntry(3L, null);
    }

    @Test
    @DisplayName("sin diferencias con lo último avisado no envía")
    void noDifferenceSendsNothing() {
        stored.add(new AutogestionNotifiedRoom(1L, 10L, Instant.now()));
        allocated(Map.of(1L, 10L));

        check.check(TODAY);

        verify(messageService, never()).send(any());
    }

    @Test
    @DisplayName("un cambio de aula dentro de los próximos 7 días envía un mensaje y actualiza la foto")
    void changeInsideWindowIsNotifiedOnce() {
        stored.add(new AutogestionNotifiedRoom(1L, 10L, Instant.now()));
        stored.add(new AutogestionNotifiedRoom(2L, null, Instant.now()));
        allocated(Map.of(1L, 11L, 2L, 10L));

        check.check(TODAY);

        ArgumentCaptor<SendCommissionMessageRequest> sent = ArgumentCaptor.forClass(SendCommissionMessageRequest.class);
        verify(messageService).send(sent.capture());
        assertThat(sent.getValue().note()).isEqualTo("Cambio de aula: 04/11 13:15 aula 220>305; 10/11 13:15 aula 220");
        assertThat(sent.getValue().untilDate()).isEqualTo(TODAY.plusDays(14));
        assertThat(sent.getValue().academicYear()).isEqualTo(2026);
        assertThat(storedRooms()).containsEntry(1L, 11L).containsEntry(2L, 10L);

        check.check(TODAY);

        verify(messageService).send(any());
    }

    @Test
    @DisplayName("un cambio posterior a 7 días no se avisa hasta que entra en la ventana")
    void changeBeyondWindowWaitsUntilItEntersWindow() {
        stored.add(new AutogestionNotifiedRoom(3L, 10L, Instant.now()));
        allocated(Map.of(3L, 11L));

        check.check(TODAY);
        verify(messageService, never()).send(any());

        check.check(TODAY.plusDays(2));

        ArgumentCaptor<SendCommissionMessageRequest> sent = ArgumentCaptor.forClass(SendCommissionMessageRequest.class);
        verify(messageService).send(sent.capture());
        assertThat(sent.getValue().note()).isEqualTo("Cambio de aula: 11/11 13:15 aula 220>305");
    }

    @Test
    @DisplayName("liberar un aula no se avisa y conserva la última aula avisada como línea base")
    void releaseIsNotNotified() {
        stored.add(new AutogestionNotifiedRoom(1L, 10L, Instant.now()));
        allocated(Map.of());

        check.check(TODAY);

        verify(messageService, never()).send(any());
        assertThat(storedRooms()).containsEntry(1L, 10L);
    }

    @Test
    @DisplayName("si el envío falla, la foto no se actualiza y el cambio se reintenta en la próxima corrida")
    void failedSendKeepsBaseline() {
        stored.add(new AutogestionNotifiedRoom(1L, 10L, Instant.now()));
        allocated(Map.of(1L, 11L));
        doThrow(new IllegalStateException("boom")).when(messageService).send(any());

        assertThatThrownBy(() -> check.check(TODAY)).isInstanceOf(IllegalStateException.class);

        assertThat(storedRooms()).containsEntry(1L, 10L);
    }
}
