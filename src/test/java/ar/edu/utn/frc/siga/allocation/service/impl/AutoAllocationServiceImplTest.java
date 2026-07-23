package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.request.AutoPreviewRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.ConfirmAutoPreviewRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.PreviewAllocationDto;
import ar.edu.utn.frc.siga.allocation.dto.request.ValidateMoveRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AutoPreviewResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ConfirmAutoPreviewResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.MoveConflictDto;
import ar.edu.utn.frc.siga.allocation.dto.response.MoveConflictDto.ConflictOrigin;
import ar.edu.utn.frc.siga.allocation.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ValidateMoveResponseDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.exception.ReassignConflictException;
import ar.edu.utn.frc.siga.allocation.mapper.AcademicEventComposer;
import ar.edu.utn.frc.siga.allocation.mapper.AllocationComposer;
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
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
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
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
    @Mock
    private AllocationComposer allocationComposer;

    @Captor
    private ArgumentCaptor<List<SolverEvent>> solverEventsCaptor;
    @Captor
    private ArgumentCaptor<List<SolverOccupancy>> occupancyCaptor;
    @Captor
    private ArgumentCaptor<List<SolverRoom>> roomsCaptor;
    @Captor
    private ArgumentCaptor<List<Allocation>> savedCaptor;

    private AutoAllocationServiceImpl service;

    @BeforeEach
    void setUp() {
        // El loader es package-private y vive en el mismo paquete: se instancia real con
        // los repos mockeados para ejercitar dedup/pinned/fechas a través del servicio.
        AutoAllocationDataLoader dataLoader = new AutoAllocationDataLoader(
                eventRepository, occurrenceRepository, allocationRepository, classroomService);
        AllocationValidator validator = new AllocationValidator(classroomService, allocationRepository);
        AllocationWriter writer = new AllocationWriter(allocationRepository, validator);
        service = new AutoAllocationServiceImpl(dataLoader, classroomService, academicEventComposer, solverService,
                occurrenceRepository, allocationComposer, validator, writer);

        lenient().when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(5, 100)));
        lenient().when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, 100)));
        lenient().when(allocationRepository.findOccupancyBetween(any(), any(), any())).thenReturn(List.of());
        lenient().when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(solverService.preview(any(), any(), any(), anyInt()))
                .thenReturn(new SolverPreview("prev_test", List.of()));
        lenient().when(academicEventComposer.composeById(ArgumentMatchers.<List<? extends AcademicEvent>>any()))
                .thenAnswer(invocation -> {
            List<AcademicEvent> events = invocation.getArgument(0);
            Map<Long, AcademicEventResponseDto> result = new LinkedHashMap<>();
            for (AcademicEvent event : events) {
                result.put(event.getId(), new RecurringEventResponseDto(event.getId(), EventType.RECURRING, event.getEnrolled(),
                        event.getStartTime(), event.getDuration().toMinutes(), null, null, null, null, null));
            }
            return result;
        });
        lenient().when(allocationComposer.composeAll(any())).thenReturn(List.of());
        lenient().when(allocationRepository.findByOccurrence_IdIn(any())).thenReturn(List.of());
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
        assertThat(solverEventsCaptor.getValue().getFirst().planningId()).isEqualTo("1");
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
        assertThat(occupancy.getFirst().classroomId()).isEqualTo(7);
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
        assertThat(solverEventsCaptor.getValue().getFirst().occurrenceDates())
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
        assertThat(result.allocations().getFirst().event().id()).isEqualTo(1L);
        assertThat(result.allocations().getFirst().classroom()).isNotNull();
        assertThat(result.unresolved()).hasSize(1);
        assertThat(result.unresolved().getFirst().event().id()).isEqualTo(2L);
    }

    @Test
    @DisplayName("unresolved: reporta un conflicto por aula candidata, contra BD y contra el resto del preview")
    void unresolvedReportaConflictosPorAulaCandidata() {
        RecurringEvent resolved = recurringEvent(1L); // toma el aula 6 en el preview
        RecurringEvent unresolvedEvent = recurringEvent(2L); // mismo horario, sin aula
        RecurringEvent foreignEvent = recurringEvent(99L);
        LocalDate date = futureDate(1);
        Allocation foreignAllocation = allocation(500L,
                occurrence(50L, foreignEvent, date, OccurrenceStatus.ASSIGNED), 5);

        when(eventRepository.findAllById(any())).thenReturn(List.of(resolved, unresolvedEvent));
        when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(
                        occurrence(10L, resolved, date, OccurrenceStatus.SCHEDULED),
                        occurrence(11L, unresolvedEvent, date, OccurrenceStatus.SCHEDULED)));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(5, 100), classroom(6, 100)));
        when(allocationRepository.findOccupancyBetween(any(), any(), eq(OccurrenceStatus.ASSIGNED)))
                .thenReturn(List.of(foreignAllocation));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(6, 100)));
        when(solverService.preview(any(), any(), any(), anyInt())).thenReturn(new SolverPreview("prev_unresolved",
                List.of(new SolverAllocation("1", 6), new SolverAllocation("2", null))));

        AutoPreviewResponseDto result = service.autoPreview(new AutoPreviewRequestDto(List.of(1L, 2L), null));

        assertThat(result.unresolved()).hasSize(1);
        List<MoveConflictDto> conflicts = result.unresolved().getFirst().conflicts();
        assertThat(conflicts).hasSize(2); // una entrada por aula candidata (5 y 6)
        assertThat(conflicts).anySatisfy(c -> {
            assertThat(c.classroomId()).isEqualTo(5);
            assertThat(c.origin()).isEqualTo(ConflictOrigin.DATABASE);
            assertThat(c.conflictingEventId()).isEqualTo(99L);
        });
        assertThat(conflicts).anySatisfy(c -> {
            assertThat(c.classroomId()).isEqualTo(6);
            assertThat(c.origin()).isEqualTo(ConflictOrigin.PREVIEW);
            assertThat(c.conflictingEventId()).isEqualTo(1L);
        });
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
    @DisplayName("autoPreview: eventId inexistente → 404, incluso con ids duplicados en el request")
    void autoPreviewEventoInexistente() {
        RecurringEvent event = recurringEvent(1L);
        when(eventRepository.findAllById(any())).thenReturn(List.of(event));

        assertThatThrownBy(() -> service.autoPreview(new AutoPreviewRequestDto(List.of(1L, 1L, 99L), null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("autoPreview: evento sin ocurrencias pendientes (ni SCHEDULED ni ASSIGNED futuras) → conflicto")
    void autoPreviewSinOcurrenciasPendientes() {
        RecurringEvent event = recurringEvent(1L);
        when(eventRepository.findAllById(any())).thenReturn(List.of(event));
        when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.autoPreview(new AutoPreviewRequestDto(List.of(1L), null)))
                .isInstanceOf(AllocationConflictException.class)
                .hasMessageContaining("ocurrencias pendientes");
    }

    @Test
    @DisplayName("Mapea el evento y el aula al modelo del solver con todos los campos correctos")
    void mapeaSolverEventYSolverRoomCorrectamente() {
        RecurringEvent event = recurringEvent(1L); // enrolled=30, 08:00-09:30
        LocalDate date = futureDate(1);
        when(eventRepository.findAllById(any())).thenReturn(List.of(event));
        when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(occurrence(10L, event, date, OccurrenceStatus.SCHEDULED)));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(5, 100)));

        service.autoPreview(new AutoPreviewRequestDto(List.of(1L), null));

        verify(solverService).preview(solverEventsCaptor.capture(), roomsCaptor.capture(), any(), anyInt());

        SolverEvent solverEvent = solverEventsCaptor.getValue().getFirst();
        assertThat(solverEvent.planningId()).isEqualTo("1");
        assertThat(solverEvent.commissionKey()).isNull();
        assertThat(solverEvent.enrolled()).isEqualTo(30);
        assertThat(solverEvent.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(solverEvent.endTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(solverEvent.occurrenceDates()).containsExactly(date);

        SolverRoom solverRoom = roomsCaptor.getValue().getFirst();
        assertThat(solverRoom.id()).isEqualTo(5);
        assertThat(solverRoom.capacity()).isEqualTo(100);
        assertThat(solverRoom.buildingId()).isEqualTo(1);
    }

    @Test
    @DisplayName("Consulta la ocupación de BD en el rango [min(startDate), max(endDate)] de los eventos seleccionados")
    void consultaOcupacionEnRangoDeFechasDeLosEventos() {
        LocalDate start1 = LocalDate.now().minusMonths(3);
        LocalDate end1 = LocalDate.now().plusMonths(1);
        LocalDate start2 = LocalDate.now().minusMonths(1);
        LocalDate end2 = LocalDate.now().plusMonths(5);
        RecurringEvent event1 = RecurringEvent.builder().id(1L).enrolled(30).startTime(LocalTime.of(8, 0))
                .duration(Duration.ofMinutes(90)).dayOfWeek(DayOfWeek.MONDAY).startDate(start1).endDate(end1).build();
        RecurringEvent event2 = RecurringEvent.builder().id(2L).enrolled(30).startTime(LocalTime.of(14, 0))
                .duration(Duration.ofMinutes(60)).dayOfWeek(DayOfWeek.MONDAY).startDate(start2).endDate(end2).build();
        when(eventRepository.findAllById(any())).thenReturn(List.of(event1, event2));
        when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(
                        occurrence(10L, event1, futureDate(1), OccurrenceStatus.SCHEDULED),
                        occurrence(11L, event2, futureDate(2), OccurrenceStatus.SCHEDULED)));

        service.autoPreview(new AutoPreviewRequestDto(List.of(1L, 2L), null));

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(allocationRepository).findOccupancyBetween(
                fromCaptor.capture(), toCaptor.capture(), eq(OccurrenceStatus.ASSIGNED));
        assertThat(fromCaptor.getValue()).isEqualTo(start1);
        assertThat(toCaptor.getValue()).isEqualTo(end2);
    }

    @Test
    @DisplayName("autoPreview: usa timeLimitSeconds=30 por defecto si el request no lo trae")
    void autoPreviewUsaTimeLimitPorDefecto() {
        RecurringEvent event = recurringEvent(1L);
        when(eventRepository.findAllById(any())).thenReturn(List.of(event));
        when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(occurrence(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED)));

        service.autoPreview(new AutoPreviewRequestDto(List.of(1L), null));

        ArgumentCaptor<Integer> timeLimitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(solverService).preview(any(), any(), any(), timeLimitCaptor.capture());
        assertThat(timeLimitCaptor.getValue()).isEqualTo(30);
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
        assertThat(result.allocations().getFirst().event().id()).isEqualTo(1L);
        assertThat(result.allocations().getFirst().event().enrolled()).isEqualTo(30);
        assertThat(result.allocations().getFirst().occurrenceDates()).containsExactly(date1, date2);
        assertThat(result.allocations().getFirst().classroom().id()).isEqualTo(5);
        assertThat(result.allocations().getFirst().overcrowdedBy()).isZero(); // capacidad 100 >= inscriptos 30
        assertThat(result.unresolved()).isEmpty();
    }

    @Test
    @DisplayName("Sobrecupo: fila resuelta con inscriptos > capacidad del aula reporta overcrowdedBy > 0")
    void sobrecupoReportaOvercrowdedBy() {
        RecurringEvent event = recurringEvent(1L); // enrolled = 30
        when(eventRepository.findAllById(any())).thenReturn(List.of(event));
        when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(occurrence(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED)));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, 20))); // capacidad 20 < 30
        when(solverService.preview(any(), any(), any(), anyInt())).thenReturn(new SolverPreview("prev_oc",
                List.of(new SolverAllocation("1", 5))));

        AutoPreviewResponseDto result = service.autoPreview(new AutoPreviewRequestDto(List.of(1L), null));

        assertThat(result.allocations()).hasSize(1);
        assertThat(result.allocations().getFirst().classroom().id()).isEqualTo(5);
        assertThat(result.allocations().getFirst().overcrowdedBy()).isEqualTo(10);
        assertThat(result.unresolved()).isEmpty();
    }

    @Test
    @DisplayName("Floor: evento ya asignado que el solver deja sin aula conserva su aula previa (no cae en unresolved)")
    void floorEventoYaAsignadoConservaAulaPrevia() {
        RecurringEvent event = recurringEvent(1L);
        LocalDate date = futureDate(1);
        Allocation prior = allocation(100L, occurrence(10L, event, date, OccurrenceStatus.ASSIGNED), 3);
        when(eventRepository.findAllById(any())).thenReturn(List.of(event));
        when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(occurrence(10L, event, date, OccurrenceStatus.ASSIGNED)));
        when(allocationRepository.findOccupancyBetween(any(), any(), eq(OccurrenceStatus.ASSIGNED)))
                .thenReturn(List.of(prior));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(3, 100)));
        when(solverService.preview(any(), any(), any(), anyInt())).thenReturn(new SolverPreview("prev_floor",
                List.of(new SolverAllocation("1", null)))); // el solver no ubicó el evento

        AutoPreviewResponseDto result = service.autoPreview(new AutoPreviewRequestDto(List.of(1L), null));

        assertThat(result.unresolved()).isEmpty();
        assertThat(result.allocations()).hasSize(1);
        assertThat(result.allocations().getFirst().event().id()).isEqualTo(1L);
        assertThat(result.allocations().getFirst().classroom().id()).isEqualTo(3); // aula previa conservada
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
        assertThat(result.allocations().getFirst().event().id()).isEqualTo(1L);
        assertThat(result.allocations().getFirst().occurrenceDates()).containsExactly(date);
        assertThat(result.allocations().getFirst().classroom().id()).isEqualTo(5);
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
        MoveConflictDto conflict = result.conflicts().getFirst();
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
        MoveConflictDto conflict = result.conflicts().getFirst();
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

    @Test
    @DisplayName("confirm: crea allocation nueva para occurrence sin asignación y actualiza la existente sin duplicar")
    void confirmCreaYActualizaAllocationsSinDuplicar() {
        RecurringEvent event1 = recurringEvent(1L);
        RecurringEvent event2 = recurringEvent(2L, LocalTime.of(14, 0), Duration.ofMinutes(60));
        LocalDate date1 = futureDate(1);
        LocalDate date2 = futureDate(2);
        Occurrence occ1 = occurrence(10L, event1, date1, OccurrenceStatus.SCHEDULED);
        Occurrence occ2 = occurrence(11L, event2, date2, OccurrenceStatus.ASSIGNED);
        Allocation existingForOcc2 = allocation(900L, occ2, 3);

        when(solverService.getPreview("prev_confirm")).thenReturn(new SolverPreview("prev_confirm",
                List.of(new SolverAllocation("1", 5), new SolverAllocation("2", 7))));
        when(eventRepository.findAllById(any())).thenReturn(List.of(event1, event2));
        when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(occ1, occ2));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, 100), classroom(7, 100)));
        when(allocationRepository.findByOccurrence_IdIn(any())).thenReturn(List.of(existingForOcc2));
        when(allocationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConfirmAutoPreviewRequestDto request = new ConfirmAutoPreviewRequestDto(
                List.of(new PreviewAllocationDto(1L, 5), new PreviewAllocationDto(2L, 7)));

        service.confirm("prev_confirm", request);
        verify(allocationComposer).composeAll(savedCaptor.capture());

        List<Allocation> saved = savedCaptor.getValue();
        assertThat(saved).hasSize(2);
        Allocation savedForOcc1 = saved.stream().filter(a -> a.getOccurrence().getId().equals(10L)).findFirst().orElseThrow();
        Allocation savedForOcc2 = saved.stream().filter(a -> a.getOccurrence().getId().equals(11L)).findFirst().orElseThrow();

        assertThat(savedForOcc1.getId()).isNull(); // nueva, todavía sin id asignado por la BD
        assertThat(savedForOcc1.getClassroomId()).isEqualTo(5);
        assertThat(savedForOcc1.getSource()).isEqualTo(AllocationSource.AUTOMATIC);

        assertThat(savedForOcc2.getId()).isEqualTo(900L); // reusa la existente, no duplica
        assertThat(savedForOcc2.getClassroomId()).isEqualTo(7);
        assertThat(savedForOcc2.getSource()).isEqualTo(AllocationSource.AUTOMATIC);

        // occ1/occ2 llegan managed; el writer ya no llama save() explícito (dirty checking).
        assertThat(occ1.getStatus()).isEqualTo(OccurrenceStatus.ASSIGNED);
        assertThat(occ2.getStatus()).isEqualTo(OccurrenceStatus.ASSIGNED);
    }

    @Test
    @DisplayName("confirm: occurrence ya ASSIGNED conserva su estado y queda con el aula nueva")
    void confirmOccurrenceAssignedConservaEstadoConAulaNueva() {
        RecurringEvent event = recurringEvent(1L);
        LocalDate date = futureDate(1);
        Occurrence occ = occurrence(10L, event, date, OccurrenceStatus.ASSIGNED);
        Allocation existing = allocation(900L, occ, 3);

        when(solverService.getPreview("prev_confirm")).thenReturn(new SolverPreview("prev_confirm",
                List.of(new SolverAllocation("1", 5))));
        when(eventRepository.findAllById(any())).thenReturn(List.of(event));
        when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(occ));
        when(allocationRepository.findByOccurrence_IdIn(any())).thenReturn(List.of(existing));

        ConfirmAutoPreviewRequestDto request = new ConfirmAutoPreviewRequestDto(
                List.of(new PreviewAllocationDto(1L, 5)));

        service.confirm("prev_confirm", request);

        assertThat(occ.getStatus()).isEqualTo(OccurrenceStatus.ASSIGNED);
        assertThat(existing.getClassroomId()).isEqualTo(5);
        assertThat(existing.getSource()).isEqualTo(AllocationSource.AUTOMATIC);
    }

    @Test
    @DisplayName("confirm: invalida el preview tras aplicar la propuesta")
    void confirmInvalidaElPreviewTrasAplicar() {
        RecurringEvent event = recurringEvent(1L);
        Occurrence occ = occurrence(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED);
        when(solverService.getPreview("prev_confirm")).thenReturn(new SolverPreview("prev_confirm",
                List.of(new SolverAllocation("1", 5))));
        when(eventRepository.findAllById(any())).thenReturn(List.of(event));
        when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(occ));
        when(allocationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.confirm("prev_confirm", new ConfirmAutoPreviewRequestDto(List.of(new PreviewAllocationDto(1L, 5))));

        verify(solverService).invalidatePreview("prev_confirm");
    }

    @Test
    @DisplayName("confirm: 410 si el preview expiró")
    void confirmPreviewExpirado() {
        when(solverService.getPreview("prev_expired")).thenThrow(new ExpiredPreviewException("prev_expired"));

        assertThatThrownBy(() -> service.confirm("prev_expired",
                new ConfirmAutoPreviewRequestDto(List.of(new PreviewAllocationDto(1L, 5)))))
                .isInstanceOf(ExpiredPreviewException.class);
    }

    @Test
    @DisplayName("confirm: 409 si un eventId del body no pertenece al preview")
    void confirmEventoFueraDelPreview() {
        when(solverService.getPreview("prev_confirm")).thenReturn(new SolverPreview("prev_confirm",
                List.of(new SolverAllocation("1", 5))));

        assertThatThrownBy(() -> service.confirm("prev_confirm",
                new ConfirmAutoPreviewRequestDto(List.of(new PreviewAllocationDto(2L, 5)))))
                .isInstanceOf(AllocationConflictException.class)
                .hasMessageContaining("2");
    }

    @Test
    @DisplayName("confirm: 409 si el body tiene eventIds duplicados")
    void confirmDuplicadosEnBody() {
        when(solverService.getPreview("prev_confirm")).thenReturn(new SolverPreview("prev_confirm",
                List.of(new SolverAllocation("1", 5))));

        assertThatThrownBy(() -> service.confirm("prev_confirm", new ConfirmAutoPreviewRequestDto(
                List.of(new PreviewAllocationDto(1L, 5), new PreviewAllocationDto(1L, 7)))))
                .isInstanceOf(AllocationConflictException.class)
                .hasMessageContaining("duplicados");

        verify(allocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirm: 409 si el aula no existe o no está disponible")
    void confirmAulaNoDisponible() {
        RecurringEvent event = recurringEvent(1L);
        when(solverService.getPreview("prev_confirm")).thenReturn(new SolverPreview("prev_confirm",
                List.of(new SolverAllocation("1", 5))));
        when(eventRepository.findAllById(any())).thenReturn(List.of(event));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, 100, false)));

        assertThatThrownBy(() -> service.confirm("prev_confirm",
                new ConfirmAutoPreviewRequestDto(List.of(new PreviewAllocationDto(1L, 5)))))
                .isInstanceOf(AllocationConflictException.class)
                .hasMessageContaining("5");

        verify(allocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirm: 409 si la propuesta choca con una asignación firme de BD, sin persistir nada")
    void confirmConflictoContraBdSinPersistir() {
        RecurringEvent event = recurringEvent(1L);
        RecurringEvent foreignEvent = recurringEvent(99L);
        LocalDate date = futureDate(1);
        Occurrence occ = occurrence(10L, event, date, OccurrenceStatus.SCHEDULED);
        Allocation foreignAllocation = allocation(500L,
                occurrence(50L, foreignEvent, date, OccurrenceStatus.ASSIGNED), 5);

        when(solverService.getPreview("prev_confirm")).thenReturn(new SolverPreview("prev_confirm",
                List.of(new SolverAllocation("1", 5))));
        when(eventRepository.findAllById(any())).thenReturn(List.of(event));
        when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(occ));
        when(allocationRepository.findOccupancyBetween(any(), any(), eq(OccurrenceStatus.ASSIGNED)))
                .thenReturn(List.of(foreignAllocation));

        assertThatThrownBy(() -> service.confirm("prev_confirm",
                new ConfirmAutoPreviewRequestDto(List.of(new PreviewAllocationDto(1L, 5)))))
                .isInstanceOf(ReassignConflictException.class);

        verify(allocationRepository, never()).save(any());
        verify(occurrenceRepository, never()).save(any());
        verify(solverService, never()).invalidatePreview(any());
    }

    @Test
    @DisplayName("confirm: 409 si dos ítems del propio set chocan entre sí (misma aula, mismo horario, fecha compartida)")
    void confirmConflictoInternoEntreDosItems() {
        RecurringEvent event1 = recurringEvent(1L);
        RecurringEvent event2 = recurringEvent(2L);
        LocalDate date = futureDate(1);
        Occurrence occ1 = occurrence(10L, event1, date, OccurrenceStatus.SCHEDULED);
        Occurrence occ2 = occurrence(11L, event2, date, OccurrenceStatus.SCHEDULED);

        when(solverService.getPreview("prev_confirm")).thenReturn(new SolverPreview("prev_confirm",
                List.of(new SolverAllocation("1", 9), new SolverAllocation("2", 9))));
        when(eventRepository.findAllById(any())).thenReturn(List.of(event1, event2));
        when(occurrenceRepository.findByEvent_IdInAndStatusInAndDateGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(occ1, occ2));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(9, 100)));

        assertThatThrownBy(() -> service.confirm("prev_confirm", new ConfirmAutoPreviewRequestDto(
                List.of(new PreviewAllocationDto(1L, 9), new PreviewAllocationDto(2L, 9)))))
                .isInstanceOf(ReassignConflictException.class);

        verify(allocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirm: classroomId null va a skippedEventIds y no se aplica")
    void confirmClassroomIdNullQuedaSkipped() {
        when(solverService.getPreview("prev_confirm")).thenReturn(new SolverPreview("prev_confirm",
                List.of(new SolverAllocation("1", null))));

        ConfirmAutoPreviewResponseDto result = service.confirm("prev_confirm",
                new ConfirmAutoPreviewRequestDto(List.of(new PreviewAllocationDto(1L, null))));

        assertThat(result.applied()).isEmpty();
        assertThat(result.skippedEventIds()).containsExactly(1L);
        verify(allocationRepository, never()).save(any());
        verify(solverService, never()).invalidatePreview(any());
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
        return classroom(id, capacity, true);
    }

    private ClassroomResponseDto classroom(Integer id, Integer capacity, boolean available) {
        return new ClassroomResponseDto(id, "Aula " + id, 1, capacity, available, 1, "Edificio 1", 1, "Tipo");
    }

    private LocalDate futureDate(int daysFromNow) {
        return LocalDate.now().plusDays(daysFromNow);
    }
}
