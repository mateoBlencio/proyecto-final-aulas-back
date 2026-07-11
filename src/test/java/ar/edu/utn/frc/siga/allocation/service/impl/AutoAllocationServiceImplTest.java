package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.request.AutoPreviewRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.PreviewAllocationDto;
import ar.edu.utn.frc.siga.allocation.dto.request.ValidateMoveRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AutoPreviewResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.MoveConflictDto;
import ar.edu.utn.frc.siga.allocation.dto.response.MoveConflictDto.ConflictOrigin;
import ar.edu.utn.frc.siga.allocation.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ValidateMoveResponseDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.mapper.AcademicEventComposer;
import ar.edu.utn.frc.siga.allocation.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.model.EventType;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.model.UniqueEvent;
import ar.edu.utn.frc.siga.allocation.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.solver.exception.ExpiredPreviewException;
import ar.edu.utn.frc.siga.solver.model.SolverAllocation;
import ar.edu.utn.frc.siga.solver.model.SolverEvent;
import ar.edu.utn.frc.siga.solver.model.SolverOccupancy;
import ar.edu.utn.frc.siga.solver.model.SolverPreview;
import ar.edu.utn.frc.siga.solver.model.SolverRoom;
import ar.edu.utn.frc.siga.solver.service.SolverService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AutoAllocationServiceImpl")
class AutoAllocationServiceImplTest {

    @Mock
    private AcademicEventRepository eventRepository;
    @Mock
    private OccurrenceRepository occurrenceRepository;
    @Mock
    private AllocationRepository allocationRepository;
    @Mock
    private ClassroomService classroomService;
    @Mock
    private SolverService solverService;
    @Mock
    private AcademicEventComposer academicEventComposer;

    @Captor
    private ArgumentCaptor<List<SolverEvent>> solverEventsCaptor;
    @Captor
    private ArgumentCaptor<List<SolverOccupancy>> occupancyCaptor;

    private AutoAllocationServiceImpl service;

    @BeforeEach
    void setUp() {
        // El loader es package-private y vive en el mismo paquete: se instancia real con
        // los repos mockeados para ejercitar dedup/pinned/fechas a través del servicio.
        AutoAllocationDataLoader dataLoader = new AutoAllocationDataLoader(
                eventRepository, occurrenceRepository, allocationRepository, classroomService);
        service = new AutoAllocationServiceImpl(dataLoader, classroomService, academicEventComposer, solverService);

        lenient().when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(5, 100)));
        lenient().when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, 100)));
        lenient().when(allocationRepository.findOccupancyBetween(any(), any(), any())).thenReturn(List.of());
        lenient().when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(solverService.preview(any(), any(), any(), anyInt()))
                .thenReturn(new SolverPreview("prev_test", List.of()));
        lenient().when(academicEventComposer.compose(any(Collection.class))).thenAnswer(invocation -> {
            Collection<AcademicEvent> events = invocation.getArgument(0);
            List<AcademicEventResponseDto> result = new ArrayList<>();
            for (AcademicEvent event : events) {
                result.add(new RecurringEventResponseDto(event.getId(), EventType.RECURRING, event.getEnrolled(),
                        event.getStartTime(), event.getDuration().toMinutes(), null, null, null, null, null));
            }
            return result;
        });
    }

    @Test
    @DisplayName("Deduplica los eventIds del request: el mismo id repetido genera un solo SolverEvent")
    void deduplicaEventIds() {
        RecurringEvent event = recurringEvent(1L);
        when(eventRepository.findAllById(any())).thenReturn(List.of(event));
        when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(occurrence(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED)));

        service.autoPreview(new AutoPreviewRequestDto(List.of(1L, 1L), null));

        verify(solverService).preview(solverEventsCaptor.capture(), any(), any(), anyInt());
        assertThat(solverEventsCaptor.getValue()).hasSize(1);
        assertThat(solverEventsCaptor.getValue().get(0).planningId()).isEqualTo("1");
    }

    @Test
    @DisplayName("Excluye de la ocupación pinned las allocations de los eventos seleccionados y conserva las ajenas")
    void excluyeOcupacionDeEventosSeleccionados() {
        RecurringEvent selected = recurringEvent(1L);
        RecurringEvent foreign = recurringEvent(2L);
        LocalDate date = futureDate(1);
        Allocation selectedAllocation = allocation(100L, occurrence(10L, selected, date, OccurrenceStatus.ASSIGNED), 5);
        Allocation foreignAllocation = allocation(101L, occurrence(11L, foreign, date, OccurrenceStatus.ASSIGNED), 7);

        when(eventRepository.findAllById(any())).thenReturn(List.of(selected));
        when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(occurrence(10L, selected, date, OccurrenceStatus.ASSIGNED)));
        when(allocationRepository.findOccupancyBetween(any(), any(), eq(OccurrenceStatus.ASSIGNED)))
                .thenReturn(List.of(selectedAllocation, foreignAllocation));

        service.autoPreview(new AutoPreviewRequestDto(List.of(1L), null));

        verify(solverService).preview(any(), any(), occupancyCaptor.capture(), anyInt());
        List<SolverOccupancy> occupancy = occupancyCaptor.getValue();
        assertThat(occupancy).hasSize(1);
        assertThat(occupancy.get(0).classroomId()).isEqualTo(7);
    }

    @Test
    @DisplayName("Incluye las occurrences ASSIGNED futuras en las fechas del solver (re-resolución)")
    void incluyeOccurrencesAssignedFuturas() {
        RecurringEvent event = recurringEvent(1L);
        LocalDate scheduledDate = futureDate(1);
        LocalDate assignedDate = futureDate(8);
        when(eventRepository.findAllById(any())).thenReturn(List.of(event));
        when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(
                        occurrence(10L, event, scheduledDate, OccurrenceStatus.SCHEDULED),
                        occurrence(11L, event, assignedDate, OccurrenceStatus.ASSIGNED)));

        service.autoPreview(new AutoPreviewRequestDto(List.of(1L), null));

        // El repo se consulta con ambos estados y solo fechas desde hoy (filtra clases dictadas).
        verify(occurrenceRepository).findByEvent_IdInAndStatusInAndDateGreaterThanEqual(
                any(), eq(List.of(OccurrenceStatus.SCHEDULED, OccurrenceStatus.ASSIGNED)), eq(LocalDate.now()));
        verify(solverService).preview(solverEventsCaptor.capture(), any(), any(), anyInt());
        assertThat(solverEventsCaptor.getValue().get(0).occurrenceDates())
                .containsExactlyInAnyOrder(scheduledDate, assignedDate);
    }

    @Test
    @DisplayName("Separa la respuesta en allocations (con aula) y unresolved (classroomId null)")
    void separaAllocationsYUnresolved() {
        RecurringEvent resolved = recurringEvent(1L);
        RecurringEvent unresolved = recurringEvent(2L);
        when(eventRepository.findAllById(any())).thenReturn(List.of(resolved, unresolved));
        when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(
                        occurrence(10L, resolved, futureDate(1), OccurrenceStatus.SCHEDULED),
                        occurrence(11L, unresolved, futureDate(1), OccurrenceStatus.SCHEDULED)));
        when(solverService.preview(any(), any(), any(), anyInt())).thenReturn(new SolverPreview("prev_abc",
                List.of(new SolverAllocation("1", 5), new SolverAllocation("2", null))));

        AutoPreviewResponseDto result = service.autoPreview(new AutoPreviewRequestDto(List.of(1L, 2L), null));

        assertThat(result.previewId()).isEqualTo("prev_abc");
        assertThat(result.allocations()).hasSize(1);
        assertThat(result.allocations().get(0).event().id()).isEqualTo(1L);
        assertThat(result.allocations().get(0).classroom()).isNotNull();
        assertThat(result.unresolved()).hasSize(1);
        assertThat(result.unresolved().get(0).event().id()).isEqualTo(2L);
        assertThat(result.unresolved().get(0).classroom()).isNull();
    }

    @Test
    @DisplayName("Lanza 409 AllocationConflictException cuando un id corresponde a un UniqueEvent")
    void lanzaConflictoConUniqueEvent() {
        UniqueEvent uniqueEvent = UniqueEvent.builder()
                .id(3L)
                .enrolled(20)
                .startTime(LocalTime.of(18, 0))
                .duration(Duration.ofMinutes(120))
                .date(futureDate(2))
                .build();
        when(eventRepository.findAllById(any())).thenReturn(List.of(uniqueEvent));

        assertThatThrownBy(() -> service.autoPreview(new AutoPreviewRequestDto(List.of(3L), null)))
                .isInstanceOf(AllocationConflictException.class)
                .hasMessageContaining("recurrentes");
    }

    @Test
    @DisplayName("Compone el DTO con el evento, sus fechas de ocurrencia y el aula propuesta")
    void componeDtoConEventoFechasYAula() {
        RecurringEvent event = recurringEvent(1L);
        LocalDate date1 = futureDate(1);
        LocalDate date2 = futureDate(8);
        when(eventRepository.findAllById(any())).thenReturn(List.of(event));
        when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(
                        occurrence(10L, event, date1, OccurrenceStatus.SCHEDULED),
                        occurrence(11L, event, date2, OccurrenceStatus.SCHEDULED)));
        when(solverService.preview(any(), any(), any(), anyInt())).thenReturn(new SolverPreview("prev_abc",
                List.of(new SolverAllocation("1", 5))));

        AutoPreviewResponseDto result = service.autoPreview(new AutoPreviewRequestDto(List.of(1L), null));

        assertThat(result.allocations()).hasSize(1);
        assertThat(result.allocations().get(0).event().id()).isEqualTo(1L);
        assertThat(result.allocations().get(0).event().enrolled()).isEqualTo(30);
        assertThat(result.allocations().get(0).occurrenceDates()).containsExactly(date1, date2);
        assertThat(result.allocations().get(0).classroom().id()).isEqualTo(5);
        assertThat(result.unresolved()).isEmpty();
    }

    @Test
    @DisplayName("getPreview recompone el DTO desde la BD para una preview vigente")
    void getPreviewRecomponeDesdeBd() {
        RecurringEvent event = recurringEvent(1L);
        LocalDate date = futureDate(1);
        when(solverService.getPreview("prev_abc")).thenReturn(new SolverPreview("prev_abc",
                List.of(new SolverAllocation("1", 5))));
        when(eventRepository.findAllById(any())).thenReturn(List.of(event));
        when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(occurrence(10L, event, date, OccurrenceStatus.SCHEDULED)));

        AutoPreviewResponseDto result = service.getPreview("prev_abc");

        assertThat(result.previewId()).isEqualTo("prev_abc");
        assertThat(result.allocations()).hasSize(1);
        assertThat(result.allocations().get(0).event().id()).isEqualTo(1L);
        assertThat(result.allocations().get(0).occurrenceDates()).containsExactly(date);
        assertThat(result.allocations().get(0).classroom().id()).isEqualTo(5);
    }

    @Test
    @DisplayName("validateMove: aula destino libre, sin conflictos")
    void validateMoveAulaLibre() {
        RecurringEvent event1 = recurringEvent(1L);
        RecurringEvent event2 = recurringEvent(2L);
        LocalDate date = futureDate(1);
        when(solverService.getPreview("prev_move")).thenReturn(new SolverPreview("prev_move",
                List.of(new SolverAllocation("1", 5), new SolverAllocation("2", 7))));
        when(eventRepository.findAllById(any())).thenReturn(List.of(event1, event2));
        when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(
                        occurrence(10L, event1, date, OccurrenceStatus.SCHEDULED),
                        occurrence(11L, event2, date, OccurrenceStatus.SCHEDULED)));

        ValidateMoveRequestDto request = new ValidateMoveRequestDto(1L, 9,
                List.of(new PreviewAllocationDto(1L, 9), new PreviewAllocationDto(2L, 7)));
        ValidateMoveResponseDto result = service.validateMove("prev_move", request);

        assertThat(result.valid()).isTrue();
        assertThat(result.conflicts()).isEmpty();
    }

    @Test
    @DisplayName("validateMove: conflicto contra asignación firme de BD de un evento ajeno al preview")
    void validateMoveConflictoDatabase() {
        RecurringEvent event1 = recurringEvent(1L);
        RecurringEvent event2 = recurringEvent(2L);
        RecurringEvent foreignEvent = recurringEvent(99L);
        LocalDate date = futureDate(1);
        Allocation foreignAllocation = allocation(500L,
                occurrence(50L, foreignEvent, date, OccurrenceStatus.ASSIGNED), 9);
        when(solverService.getPreview("prev_move")).thenReturn(new SolverPreview("prev_move",
                List.of(new SolverAllocation("1", 5), new SolverAllocation("2", 7))));
        when(eventRepository.findAllById(any())).thenReturn(List.of(event1, event2));
        when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(
                        occurrence(10L, event1, date, OccurrenceStatus.SCHEDULED),
                        occurrence(11L, event2, date, OccurrenceStatus.SCHEDULED)));
        when(allocationRepository.findOccupancyBetween(any(), any(), eq(OccurrenceStatus.ASSIGNED)))
                .thenReturn(List.of(foreignAllocation));

        ValidateMoveRequestDto request = new ValidateMoveRequestDto(1L, 9,
                List.of(new PreviewAllocationDto(1L, 9), new PreviewAllocationDto(2L, 7)));
        ValidateMoveResponseDto result = service.validateMove("prev_move", request);

        assertThat(result.valid()).isFalse();
        assertThat(result.conflicts()).hasSize(1);
        MoveConflictDto conflict = result.conflicts().get(0);
        assertThat(conflict.date()).isEqualTo(date);
        assertThat(conflict.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(conflict.endTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(conflict.classroomId()).isEqualTo(9);
        assertThat(conflict.conflictingEventId()).isEqualTo(99L);
        assertThat(conflict.origin()).isEqualTo(ConflictOrigin.DATABASE);
    }

    @Test
    @DisplayName("validateMove: conflicto contra otro ítem de la propuesta ajustada en el aula destino")
    void validateMoveConflictoPreview() {
        RecurringEvent event1 = recurringEvent(1L);
        RecurringEvent event2 = recurringEvent(2L);
        LocalDate date = futureDate(1);
        when(solverService.getPreview("prev_move")).thenReturn(new SolverPreview("prev_move",
                List.of(new SolverAllocation("1", 5), new SolverAllocation("2", 7))));
        when(eventRepository.findAllById(any())).thenReturn(List.of(event1, event2));
        when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(
                        occurrence(10L, event1, date, OccurrenceStatus.SCHEDULED),
                        occurrence(11L, event2, date, OccurrenceStatus.SCHEDULED)));

        ValidateMoveRequestDto request = new ValidateMoveRequestDto(1L, 7,
                List.of(new PreviewAllocationDto(1L, 7), new PreviewAllocationDto(2L, 7)));
        ValidateMoveResponseDto result = service.validateMove("prev_move", request);

        assertThat(result.valid()).isFalse();
        assertThat(result.conflicts()).hasSize(1);
        MoveConflictDto conflict = result.conflicts().get(0);
        assertThat(conflict.date()).isEqualTo(date);
        assertThat(conflict.classroomId()).isEqualTo(7);
        assertThat(conflict.conflictingEventId()).isEqualTo(2L);
        assertThat(conflict.origin()).isEqualTo(ConflictOrigin.PREVIEW);
    }

    @Test
    @DisplayName("validateMove: la ocupación de BD de un evento del propio preview no cuenta (está liberada)")
    void validateMoveIgnoraOcupacionDeEventosDelPreview() {
        RecurringEvent event1 = recurringEvent(1L);
        RecurringEvent event2 = recurringEvent(2L);
        LocalDate date = futureDate(1);
        Allocation event2Allocation = allocation(600L,
                occurrence(60L, event2, date, OccurrenceStatus.ASSIGNED), 9);
        when(solverService.getPreview("prev_move")).thenReturn(new SolverPreview("prev_move",
                List.of(new SolverAllocation("1", 5), new SolverAllocation("2", 7))));
        when(eventRepository.findAllById(any())).thenReturn(List.of(event1, event2));
        when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(
                        occurrence(10L, event1, date, OccurrenceStatus.SCHEDULED),
                        occurrence(11L, event2, date, OccurrenceStatus.SCHEDULED)));
        when(allocationRepository.findOccupancyBetween(any(), any(), eq(OccurrenceStatus.ASSIGNED)))
                .thenReturn(List.of(event2Allocation));

        ValidateMoveRequestDto request = new ValidateMoveRequestDto(1L, 9,
                List.of(new PreviewAllocationDto(1L, 9), new PreviewAllocationDto(2L, 7)));
        ValidateMoveResponseDto result = service.validateMove("prev_move", request);

        assertThat(result.valid()).isTrue();
        assertThat(result.conflicts()).isEmpty();
    }

    @Test
    @DisplayName("validateMove: 410 si el preview expiró")
    void validateMovePreviewExpirado() {
        when(solverService.getPreview("prev_expired")).thenThrow(new ExpiredPreviewException("prev_expired"));

        assertThatThrownBy(() -> service.validateMove("prev_expired",
                new ValidateMoveRequestDto(1L, 9, List.of())))
                .isInstanceOf(ExpiredPreviewException.class);
    }

    @Test
    @DisplayName("validateMove: 409 si el eventId movido no pertenece al preview")
    void validateMoveEventoAjenoAlPreview() {
        when(solverService.getPreview("prev_move")).thenReturn(new SolverPreview("prev_move",
                List.of(new SolverAllocation("1", 5), new SolverAllocation("2", 7))));

        ValidateMoveRequestDto request = new ValidateMoveRequestDto(3L, 9, List.of(new PreviewAllocationDto(1L, 5)));

        assertThatThrownBy(() -> service.validateMove("prev_move", request))
                .isInstanceOf(AllocationConflictException.class)
                .hasMessageContaining("3");
    }

    @Test
    @DisplayName("validateMove: franjas adyacentes (fin del movido == inicio del ocupante) no conflictúan")
    void validateMoveFranjasAdyacentesNoConflictuan() {
        RecurringEvent event1 = recurringEvent(1L);
        RecurringEvent adjacentEvent = recurringEvent(99L, LocalTime.of(9, 30), Duration.ofMinutes(60));
        LocalDate date = futureDate(1);
        Allocation adjacentAllocation = allocation(700L,
                occurrence(70L, adjacentEvent, date, OccurrenceStatus.ASSIGNED), 9);
        when(solverService.getPreview("prev_move")).thenReturn(new SolverPreview("prev_move",
                List.of(new SolverAllocation("1", 5))));
        when(eventRepository.findAllById(any())).thenReturn(List.of(event1));
        when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(occurrence(10L, event1, date, OccurrenceStatus.SCHEDULED)));
        when(allocationRepository.findOccupancyBetween(any(), any(), eq(OccurrenceStatus.ASSIGNED)))
                .thenReturn(List.of(adjacentAllocation));

        ValidateMoveRequestDto request = new ValidateMoveRequestDto(1L, 9, List.of(new PreviewAllocationDto(1L, 9)));
        ValidateMoveResponseDto result = service.validateMove("prev_move", request);

        assertThat(result.valid()).isTrue();
        assertThat(result.conflicts()).isEmpty();
    }

    private RecurringEvent recurringEvent(long id) {
        return recurringEvent(id, LocalTime.of(8, 0), Duration.ofMinutes(90));
    }

    private RecurringEvent recurringEvent(long id, LocalTime startTime, Duration duration) {
        return RecurringEvent.builder()
                .id(id)
                .enrolled(30)
                .startTime(startTime)
                .duration(duration)
                .dayOfWeek(DayOfWeek.MONDAY)
                .startDate(LocalDate.now().minusMonths(1))
                .endDate(LocalDate.now().plusMonths(4))
                .build();
    }

    private Occurrence occurrence(long id, AcademicEvent event, LocalDate date, OccurrenceStatus status) {
        return Occurrence.builder()
                .id(id)
                .event(event)
                .date(date)
                .status(status)
                .build();
    }

    private Allocation allocation(long id, Occurrence occurrence, Integer classroomId) {
        return Allocation.builder()
                .id(id)
                .occurrence(occurrence)
                .classroomId(classroomId)
                .source(AllocationSource.MANUAL)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ClassroomResponseDto classroom(Integer id, Integer capacity) {
        return new ClassroomResponseDto(id, "Aula " + id, 1, capacity, true, 1, "Edificio 1", 1, "Tipo");
    }

    private LocalDate futureDate(int daysFromNow) {
        return LocalDate.now().plusDays(daysFromNow);
    }
}
