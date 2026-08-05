package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.request.AutoPreviewRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.ConfirmAutoPreviewRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.PreviewAllocationDto;
import ar.edu.utn.frc.siga.allocation.dto.request.ValidateMoveRequestDto;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AutoPreviewResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ConfirmAutoPreviewResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.MoveConflictDto;
import ar.edu.utn.frc.siga.allocation.dto.response.MoveConflictDto.ConflictOrigin;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ValidateMoveResponseDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.exception.ReassignConflictException;
import ar.edu.utn.frc.siga.allocation.mapper.AllocationComposer;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.allocation.service.AllocationProblemService;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator;
import ar.edu.utn.frc.siga.common.exception.InvalidSelectionException;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AutoAllocationServiceImpl")
class AutoAllocationServiceImplTest {

    @Mock
    private AcademicEventService academicEventService;
    @Mock
    private AllocationRepository allocationRepository;
    @Mock
    private OccurrenceService occurrenceService;
    @Mock
    private ClassroomService classroomService;
    @Mock
    private SolverService solverService;
    @Mock
    private AllocationComposer allocationComposer;
    @Mock
    private AllocationProblemService allocationProblemService;

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
        // las fachadas mockeadas para ejercitar dedup/pinned/fechas a través del servicio.
        AutoAllocationDataLoader dataLoader = new AutoAllocationDataLoader(
                academicEventService, occurrenceService, allocationRepository, classroomService);
        AllocationValidator validator = new AllocationValidator(classroomService, allocationRepository, occurrenceService);
        AllocationWriter writer = new AllocationWriter(allocationRepository, validator, occurrenceService);
        service = new AutoAllocationServiceImpl(dataLoader, classroomService, solverService,
                occurrenceService, allocationComposer, validator, writer, allocationProblemService);

        lenient().when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(5, 100)));
        lenient().when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, 100)));
        lenient().when(occurrenceService.findSlotsByStatusBetween(any(), any(), any())).thenReturn(List.of());
        lenient().when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any())).thenReturn(List.of());
        lenient().when(solverService.preview(any(), any(), any(), anyInt()))
                .thenReturn(new SolverPreview("prev_test", List.of()));
        lenient().when(allocationComposer.composeAll(any())).thenReturn(List.of());
        lenient().when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Deduplica los eventIds del request: el mismo id repetido genera un solo SolverEvent")
    void deduplicaEventIds() {
        RecurringEventResponseDto event = recurringEvent(1L);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any()))
                .thenReturn(List.of(occurrenceSlot(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED)));

        service.autoPreview(new AutoPreviewRequestDto(List.of(1L, 1L), null, null, null));

        verify(solverService).preview(solverEventsCaptor.capture(), any(), any(), anyInt());
        assertThat(solverEventsCaptor.getValue()).hasSize(1);
        assertThat(solverEventsCaptor.getValue().getFirst().planningId()).isEqualTo("1");
    }

    @Test
    @DisplayName("selectAll=true resuelve contra todos los eventos sin aula, descontando excludedIds")
    void selectAllResuelveEventosSinAulaDescontandoExcluidos() {
        RecurringEventResponseDto kept = recurringEvent(1L);
        when(allocationProblemService.resolveAllUnassignedEventIds()).thenReturn(List.of(1L, 2L));
        when(academicEventService.findByIds(any())).thenReturn(List.of(kept));
        when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any()))
                .thenReturn(List.of(occurrenceSlot(10L, kept, futureDate(1), OccurrenceStatus.SCHEDULED)));

        service.autoPreview(new AutoPreviewRequestDto(null, true, List.of(2L), null));

        verify(solverService).preview(solverEventsCaptor.capture(), any(), any(), anyInt());
        assertThat(solverEventsCaptor.getValue()).extracting(SolverEvent::planningId).containsExactly("1");
    }

    @Test
    @DisplayName("Sin eventIds y sin selectAll → InvalidSelectionException")
    void sinEventIdsNiSelectAllLanzaExcepcion() {
        assertThatThrownBy(() -> service.autoPreview(new AutoPreviewRequestDto(null, null, null, null)))
                .isInstanceOf(InvalidSelectionException.class);
    }

    @Test
    @DisplayName("eventIds y selectAll=true a la vez → InvalidSelectionException")
    void eventIdsYSelectAllJuntosLanzaExcepcion() {
        assertThatThrownBy(() -> service.autoPreview(new AutoPreviewRequestDto(List.of(1L), true, null, null)))
                .isInstanceOf(InvalidSelectionException.class);
    }

    @Test
    @DisplayName("Excluye de la ocupación pinned las allocations de los eventos seleccionados y conserva las ajenas")
    void excluyeOcupacionDeEventosSeleccionados() {
        RecurringEventResponseDto selected = recurringEvent(1L);
        RecurringEventResponseDto foreign = recurringEvent(2L);
        LocalDate date = futureDate(1);
        OccurrenceSlotDto selectedSlot = occurrenceSlot(10L, selected, date, OccurrenceStatus.ASSIGNED);
        OccurrenceSlotDto foreignSlot = occurrenceSlot(11L, foreign, date, OccurrenceStatus.ASSIGNED);
        Allocation selectedAllocation = allocation(100L, 10L, 5);
        Allocation foreignAllocation = allocation(101L, 11L, 7);

        when(academicEventService.findByIds(any())).thenReturn(List.of(selected));
        when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any()))
                .thenReturn(List.of(occurrenceSlot(10L, selected, date, OccurrenceStatus.ASSIGNED)));
        when(occurrenceService.findSlotsByStatusBetween(any(), any(), any()))
                .thenReturn(List.of(selectedSlot, foreignSlot));
        when(allocationRepository.findByOccurrenceIdIn(any()))
                .thenReturn(List.of(selectedAllocation, foreignAllocation));

        service.autoPreview(new AutoPreviewRequestDto(List.of(1L), null, null, null));

        verify(solverService).preview(any(), any(), occupancyCaptor.capture(), anyInt());
        List<SolverOccupancy> occupancy = occupancyCaptor.getValue();
        assertThat(occupancy).hasSize(1);
        assertThat(occupancy.getFirst().classroomId()).isEqualTo(7);
    }

    @Test
    @DisplayName("Incluye las occurrences ASSIGNED futuras en las fechas del solver (re-resolución)")
    void incluyeOccurrencesAssignedFuturas() {
        RecurringEventResponseDto event = recurringEvent(1L);
        LocalDate scheduledDate = futureDate(1);
        LocalDate assignedDate = futureDate(8);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any()))
                .thenReturn(List.of(
                        occurrenceSlot(10L, event, scheduledDate, OccurrenceStatus.SCHEDULED),
                        occurrenceSlot(11L, event, assignedDate, OccurrenceStatus.ASSIGNED)));

        service.autoPreview(new AutoPreviewRequestDto(List.of(1L), null, null, null));

        // Se consulta con ambos estados y solo fechas desde hoy (filtra clases dictadas).
        verify(occurrenceService).findSlotsByEventsAndStatuses(
                any(), eq(List.of(OccurrenceStatus.SCHEDULED, OccurrenceStatus.ASSIGNED)), eq(LocalDate.now()));
        verify(solverService).preview(solverEventsCaptor.capture(), any(), any(), anyInt());
        assertThat(solverEventsCaptor.getValue().getFirst().occurrenceDates())
                .containsExactlyInAnyOrder(scheduledDate, assignedDate);
    }

    @Test
    @DisplayName("Separa la respuesta en allocations (con aula) y unresolved (classroomId null)")
    void separaAllocationsYUnresolved() {
        RecurringEventResponseDto resolved = recurringEvent(1L);
        RecurringEventResponseDto unresolved = recurringEvent(2L);
        when(academicEventService.findByIds(any())).thenReturn(List.of(resolved, unresolved));
        when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any()))
                .thenReturn(List.of(
                        occurrenceSlot(10L, resolved, futureDate(1), OccurrenceStatus.SCHEDULED),
                        occurrenceSlot(11L, unresolved, futureDate(1), OccurrenceStatus.SCHEDULED)));
        when(solverService.preview(any(), any(), any(), anyInt())).thenReturn(new SolverPreview("prev_abc",
                List.of(new SolverAllocation("1", 5), new SolverAllocation("2", null))));

        AutoPreviewResponseDto result = service.autoPreview(new AutoPreviewRequestDto(List.of(1L, 2L), null, null, null));

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
        RecurringEventResponseDto resolved = recurringEvent(1L); // toma el aula 6 en el preview
        RecurringEventResponseDto unresolvedEvent = recurringEvent(2L); // mismo horario, sin aula
        RecurringEventResponseDto foreignEvent = recurringEvent(99L);
        LocalDate date = futureDate(1);
        OccurrenceSlotDto foreignSlot = occurrenceSlot(50L, foreignEvent, date, OccurrenceStatus.ASSIGNED);
        Allocation foreignAllocation = allocation(500L, 50L, 5);

        when(academicEventService.findByIds(any())).thenReturn(List.of(resolved, unresolvedEvent));
        when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any()))
                .thenReturn(List.of(
                        occurrenceSlot(10L, resolved, date, OccurrenceStatus.SCHEDULED),
                        occurrenceSlot(11L, unresolvedEvent, date, OccurrenceStatus.SCHEDULED)));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(5, 100), classroom(6, 100)));
        when(occurrenceService.findSlotsByStatusBetween(any(), any(), any())).thenReturn(List.of(foreignSlot));
        when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of(foreignAllocation));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(6, 100)));
        when(solverService.preview(any(), any(), any(), anyInt())).thenReturn(new SolverPreview("prev_unresolved",
                List.of(new SolverAllocation("1", 6), new SolverAllocation("2", null))));

        AutoPreviewResponseDto result = service.autoPreview(new AutoPreviewRequestDto(List.of(1L, 2L), null, null, null));

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
        UniqueEventResponseDto uniqueEvent = new UniqueEventResponseDto(3L, EventType.UNIQUE_EVENT,
                UniqueEventKind.PARCIAL, 20, LocalTime.of(18, 0), 120, futureDate(2), null, null, null);
        when(academicEventService.findByIds(any())).thenReturn(List.of(uniqueEvent));

        assertThatThrownBy(() -> service.autoPreview(new AutoPreviewRequestDto(List.of(3L), null, null, null)))
                .isInstanceOf(AllocationConflictException.class)
                .hasMessageContaining("recurrentes");
    }

    @Test
    @DisplayName("autoPreview: eventId inexistente → 404, incluso con ids duplicados en el request")
    void autoPreviewEventoInexistente() {
        RecurringEventResponseDto event = recurringEvent(1L);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));

        assertThatThrownBy(() -> service.autoPreview(new AutoPreviewRequestDto(List.of(1L, 1L, 99L), null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("autoPreview: evento sin ocurrencias pendientes (ni SCHEDULED ni ASSIGNED futuras) → conflicto")
    void autoPreviewSinOcurrenciasPendientes() {
        RecurringEventResponseDto event = recurringEvent(1L);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.autoPreview(new AutoPreviewRequestDto(List.of(1L), null, null, null)))
                .isInstanceOf(AllocationConflictException.class)
                .hasMessageContaining("ocurrencias pendientes");
    }

    @Test
    @DisplayName("Mapea el evento y el aula al modelo del solver con todos los campos correctos")
    void mapeaSolverEventYSolverRoomCorrectamente() {
        RecurringEventResponseDto event = recurringEvent(1L); // enrolled=30, 08:00-09:30
        LocalDate date = futureDate(1);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any()))
                .thenReturn(List.of(occurrenceSlot(10L, event, date, OccurrenceStatus.SCHEDULED)));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(5, 100)));

        service.autoPreview(new AutoPreviewRequestDto(List.of(1L), null, null, null));

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
        RecurringEventResponseDto event1 = new RecurringEventResponseDto(1L, EventType.RECURRING, 30,
                LocalTime.of(8, 0), 90, DayOfWeek.MONDAY, start1, end1, null, null);
        RecurringEventResponseDto event2 = new RecurringEventResponseDto(2L, EventType.RECURRING, 30,
                LocalTime.of(14, 0), 60, DayOfWeek.MONDAY, start2, end2, null, null);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event1, event2));
        when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any()))
                .thenReturn(List.of(
                        occurrenceSlot(10L, event1, futureDate(1), OccurrenceStatus.SCHEDULED),
                        occurrenceSlot(11L, event2, futureDate(2), OccurrenceStatus.SCHEDULED)));

        service.autoPreview(new AutoPreviewRequestDto(List.of(1L, 2L), null, null, null));

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(occurrenceService).findSlotsByStatusBetween(
                eq(OccurrenceStatus.ASSIGNED), fromCaptor.capture(), toCaptor.capture());
        assertThat(fromCaptor.getValue()).isEqualTo(start1);
        assertThat(toCaptor.getValue()).isEqualTo(end2);
    }

    @Test
    @DisplayName("autoPreview: usa timeLimitSeconds=30 por defecto si el request no lo trae")
    void autoPreviewUsaTimeLimitPorDefecto() {
        RecurringEventResponseDto event = recurringEvent(1L);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any()))
                .thenReturn(List.of(occurrenceSlot(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED)));

        service.autoPreview(new AutoPreviewRequestDto(List.of(1L), null, null, null));

        ArgumentCaptor<Integer> timeLimitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(solverService).preview(any(), any(), any(), timeLimitCaptor.capture());
        assertThat(timeLimitCaptor.getValue()).isEqualTo(30);
    }

    @Test
    @DisplayName("Compone el DTO con el evento, sus fechas de ocurrencia y el aula propuesta")
    void componeDtoConEventoFechasYAula() {
        RecurringEventResponseDto event = recurringEvent(1L);
        LocalDate date1 = futureDate(1);
        LocalDate date2 = futureDate(8);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any()))
                .thenReturn(List.of(
                        occurrenceSlot(10L, event, date1, OccurrenceStatus.SCHEDULED),
                        occurrenceSlot(11L, event, date2, OccurrenceStatus.SCHEDULED)));
        when(solverService.preview(any(), any(), any(), anyInt())).thenReturn(new SolverPreview("prev_abc",
                List.of(new SolverAllocation("1", 5))));

        AutoPreviewResponseDto result = service.autoPreview(new AutoPreviewRequestDto(List.of(1L), null, null, null));

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
        RecurringEventResponseDto event = recurringEvent(1L); // enrolled = 30
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any()))
                .thenReturn(List.of(occurrenceSlot(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED)));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, 20))); // capacidad 20 < 30
        when(solverService.preview(any(), any(), any(), anyInt())).thenReturn(new SolverPreview("prev_oc",
                List.of(new SolverAllocation("1", 5))));

        AutoPreviewResponseDto result = service.autoPreview(new AutoPreviewRequestDto(List.of(1L), null, null, null));

        assertThat(result.allocations()).hasSize(1);
        assertThat(result.allocations().getFirst().classroom().id()).isEqualTo(5);
        assertThat(result.allocations().getFirst().overcrowdedBy()).isEqualTo(10);
        assertThat(result.unresolved()).isEmpty();
    }

    @Test
    @DisplayName("Floor: evento ya asignado que el solver deja sin aula conserva su aula previa (no cae en unresolved)")
    void floorEventoYaAsignadoConservaAulaPrevia() {
        RecurringEventResponseDto event = recurringEvent(1L);
        LocalDate date = futureDate(1);
        OccurrenceSlotDto priorSlot = occurrenceSlot(10L, event, date, OccurrenceStatus.ASSIGNED);
        Allocation prior = allocation(100L, 10L, 3);
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any()))
                .thenReturn(List.of(occurrenceSlot(10L, event, date, OccurrenceStatus.ASSIGNED)));
        when(occurrenceService.findSlotsByStatusBetween(any(), any(), any())).thenReturn(List.of(priorSlot));
        when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of(prior));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(3, 100)));
        when(solverService.preview(any(), any(), any(), anyInt())).thenReturn(new SolverPreview("prev_floor",
                List.of(new SolverAllocation("1", null)))); // el solver no ubicó el evento

        AutoPreviewResponseDto result = service.autoPreview(new AutoPreviewRequestDto(List.of(1L), null, null, null));

        assertThat(result.unresolved()).isEmpty();
        assertThat(result.allocations()).hasSize(1);
        assertThat(result.allocations().getFirst().event().id()).isEqualTo(1L);
        assertThat(result.allocations().getFirst().classroom().id()).isEqualTo(3); // aula previa conservada
    }

    @Test
    @DisplayName("getPreview recompone el DTO desde la BD para una preview vigente")
    void getPreviewRecomponeDesdeBd() {
        RecurringEventResponseDto event = recurringEvent(1L);
        LocalDate date = futureDate(1);
        when(solverService.getPreview("prev_abc")).thenReturn(new SolverPreview("prev_abc",
                List.of(new SolverAllocation("1", 5))));
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any()))
                .thenReturn(List.of(occurrenceSlot(10L, event, date, OccurrenceStatus.SCHEDULED)));

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
        RecurringEventResponseDto event1 = recurringEvent(1L);
        RecurringEventResponseDto event2 = recurringEvent(2L);
        LocalDate date = futureDate(1);
        when(solverService.getPreview("prev_move")).thenReturn(new SolverPreview("prev_move",
                List.of(new SolverAllocation("1", 5), new SolverAllocation("2", 7))));
        when(academicEventService.findByIds(any())).thenReturn(List.of(event1, event2));
        when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any()))
                .thenReturn(List.of(
                        occurrenceSlot(10L, event1, date, OccurrenceStatus.SCHEDULED),
                        occurrenceSlot(11L, event2, date, OccurrenceStatus.SCHEDULED)));

        ValidateMoveRequestDto request = new ValidateMoveRequestDto(1L, 9,
                List.of(new PreviewAllocationDto(1L, 9), new PreviewAllocationDto(2L, 7)));
        ValidateMoveResponseDto result = service.validateMove("prev_move", request);

        assertThat(result.valid()).isTrue();
        assertThat(result.conflicts()).isEmpty();
    }

    @Test
    @DisplayName("validateMove: conflicto contra asignación firme de BD de un evento ajeno al preview")
    void validateMoveConflictoDatabase() {
        RecurringEventResponseDto event1 = recurringEvent(1L);
        RecurringEventResponseDto event2 = recurringEvent(2L);
        RecurringEventResponseDto foreignEvent = recurringEvent(99L);
        LocalDate date = futureDate(1);
        OccurrenceSlotDto foreignSlot = occurrenceSlot(50L, foreignEvent, date, OccurrenceStatus.ASSIGNED);
        Allocation foreignAllocation = allocation(500L, 50L, 9);
        when(solverService.getPreview("prev_move")).thenReturn(new SolverPreview("prev_move",
                List.of(new SolverAllocation("1", 5), new SolverAllocation("2", 7))));
        when(academicEventService.findByIds(any())).thenReturn(List.of(event1, event2));
        when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any()))
                .thenReturn(List.of(
                        occurrenceSlot(10L, event1, date, OccurrenceStatus.SCHEDULED),
                        occurrenceSlot(11L, event2, date, OccurrenceStatus.SCHEDULED)));
        when(occurrenceService.findSlotsByStatusBetween(any(), any(), any())).thenReturn(List.of(foreignSlot));
        when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of(foreignAllocation));

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
        RecurringEventResponseDto event1 = recurringEvent(1L);
        RecurringEventResponseDto event2 = recurringEvent(2L);
        LocalDate date = futureDate(1);
        when(solverService.getPreview("prev_move")).thenReturn(new SolverPreview("prev_move",
                List.of(new SolverAllocation("1", 5), new SolverAllocation("2", 7))));
        when(academicEventService.findByIds(any())).thenReturn(List.of(event1, event2));
        when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any()))
                .thenReturn(List.of(
                        occurrenceSlot(10L, event1, date, OccurrenceStatus.SCHEDULED),
                        occurrenceSlot(11L, event2, date, OccurrenceStatus.SCHEDULED)));

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
        RecurringEventResponseDto event1 = recurringEvent(1L);
        RecurringEventResponseDto event2 = recurringEvent(2L);
        LocalDate date = futureDate(1);
        OccurrenceSlotDto event2Slot = occurrenceSlot(60L, event2, date, OccurrenceStatus.ASSIGNED);
        Allocation event2Allocation = allocation(600L, 60L, 9);
        when(solverService.getPreview("prev_move")).thenReturn(new SolverPreview("prev_move",
                List.of(new SolverAllocation("1", 5), new SolverAllocation("2", 7))));
        when(academicEventService.findByIds(any())).thenReturn(List.of(event1, event2));
        when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any()))
                .thenReturn(List.of(
                        occurrenceSlot(10L, event1, date, OccurrenceStatus.SCHEDULED),
                        occurrenceSlot(11L, event2, date, OccurrenceStatus.SCHEDULED)));
        when(occurrenceService.findSlotsByStatusBetween(any(), any(), any())).thenReturn(List.of(event2Slot));
        when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of(event2Allocation));

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
        RecurringEventResponseDto event1 = recurringEvent(1L);
        RecurringEventResponseDto adjacentEvent = recurringEvent(99L, LocalTime.of(9, 30), Duration.ofMinutes(60));
        LocalDate date = futureDate(1);
        OccurrenceSlotDto adjacentSlot = occurrenceSlot(70L, adjacentEvent, date, OccurrenceStatus.ASSIGNED);
        Allocation adjacentAllocation = allocation(700L, 70L, 9);
        when(solverService.getPreview("prev_move")).thenReturn(new SolverPreview("prev_move",
                List.of(new SolverAllocation("1", 5))));
        when(academicEventService.findByIds(any())).thenReturn(List.of(event1));
        when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any()))
                .thenReturn(List.of(occurrenceSlot(10L, event1, date, OccurrenceStatus.SCHEDULED)));
        when(occurrenceService.findSlotsByStatusBetween(any(), any(), any())).thenReturn(List.of(adjacentSlot));
        when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of(adjacentAllocation));

        ValidateMoveRequestDto request = new ValidateMoveRequestDto(1L, 9, List.of(new PreviewAllocationDto(1L, 9)));
        ValidateMoveResponseDto result = service.validateMove("prev_move", request);

        assertThat(result.valid()).isTrue();
        assertThat(result.conflicts()).isEmpty();
    }

    @Test
    @DisplayName("confirm: crea allocation nueva para occurrence sin asignación y actualiza la existente sin duplicar")
    void confirmCreaYActualizaAllocationsSinDuplicar() {
        RecurringEventResponseDto event1 = recurringEvent(1L);
        RecurringEventResponseDto event2 = recurringEvent(2L, LocalTime.of(14, 0), Duration.ofMinutes(60));
        LocalDate date1 = futureDate(1);
        LocalDate date2 = futureDate(2);
        OccurrenceSlotDto occ1 = occurrenceSlot(10L, event1, date1, OccurrenceStatus.SCHEDULED);
        OccurrenceSlotDto occ2 = occurrenceSlot(11L, event2, date2, OccurrenceStatus.ASSIGNED);
        Allocation existingForOcc2 = allocation(900L, 11L, 3);

        when(solverService.getPreview("prev_confirm")).thenReturn(new SolverPreview("prev_confirm",
                List.of(new SolverAllocation("1", 5), new SolverAllocation("2", 7))));
        when(academicEventService.findByIds(any())).thenReturn(List.of(event1, event2));
        when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any()))
                .thenReturn(List.of(occ1, occ2));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, 100), classroom(7, 100)));
        // El mismo repo lo consulta también dataLoader.load() para la ocupación (con el set vacío
        // del default lenient); el argThat evita que esa llamada reciba por error la allocation existente.
        when(allocationRepository.findByOccurrenceIdIn(argThat(ids -> ids.contains(11L))))
                .thenReturn(List.of(existingForOcc2));
        when(allocationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConfirmAutoPreviewRequestDto request = new ConfirmAutoPreviewRequestDto(
                List.of(new PreviewAllocationDto(1L, 5), new PreviewAllocationDto(2L, 7)));

        service.confirm("prev_confirm", request);
        verify(allocationComposer).composeAll(savedCaptor.capture());

        List<Allocation> saved = savedCaptor.getValue();
        assertThat(saved).hasSize(2);
        Allocation savedForOcc1 = saved.stream().filter(a -> a.getOccurrenceId().equals(10L)).findFirst().orElseThrow();
        Allocation savedForOcc2 = saved.stream().filter(a -> a.getOccurrenceId().equals(11L)).findFirst().orElseThrow();

        assertThat(savedForOcc1.getId()).isNull(); // nueva, todavía sin id asignado por la BD
        assertThat(savedForOcc1.getClassroomId()).isEqualTo(5);
        assertThat(savedForOcc1.getSource()).isEqualTo(AllocationSource.AUTOMATIC);

        assertThat(savedForOcc2.getId()).isEqualTo(900L); // reusa la existente, no duplica
        assertThat(savedForOcc2.getClassroomId()).isEqualTo(7);
        assertThat(savedForOcc2.getSource()).isEqualTo(AllocationSource.AUTOMATIC);

        // el writer pasa las occurrences asignadas a ASSIGNED vía el servicio, no mutando entidades.
        verify(occurrenceService).markAssigned(List.of(10L, 11L));
    }

    @Test
    @DisplayName("confirm: occurrence ya ASSIGNED conserva su estado y queda con el aula nueva")
    void confirmOccurrenceAssignedConservaEstadoConAulaNueva() {
        RecurringEventResponseDto event = recurringEvent(1L);
        LocalDate date = futureDate(1);
        OccurrenceSlotDto occ = occurrenceSlot(10L, event, date, OccurrenceStatus.ASSIGNED);
        Allocation existing = allocation(900L, 10L, 3);

        when(solverService.getPreview("prev_confirm")).thenReturn(new SolverPreview("prev_confirm",
                List.of(new SolverAllocation("1", 5))));
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any()))
                .thenReturn(List.of(occ));
        when(allocationRepository.findByOccurrenceIdIn(argThat(ids -> ids.contains(10L))))
                .thenReturn(List.of(existing));

        ConfirmAutoPreviewRequestDto request = new ConfirmAutoPreviewRequestDto(
                List.of(new PreviewAllocationDto(1L, 5)));

        service.confirm("prev_confirm", request);

        verify(occurrenceService).markAssigned(List.of(10L));
        assertThat(existing.getClassroomId()).isEqualTo(5);
        assertThat(existing.getSource()).isEqualTo(AllocationSource.AUTOMATIC);
    }

    @Test
    @DisplayName("confirm: invalida el preview tras aplicar la propuesta")
    void confirmInvalidaElPreviewTrasAplicar() {
        RecurringEventResponseDto event = recurringEvent(1L);
        OccurrenceSlotDto occ = occurrenceSlot(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED);
        when(solverService.getPreview("prev_confirm")).thenReturn(new SolverPreview("prev_confirm",
                List.of(new SolverAllocation("1", 5))));
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any()))
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
        RecurringEventResponseDto event = recurringEvent(1L);
        when(solverService.getPreview("prev_confirm")).thenReturn(new SolverPreview("prev_confirm",
                List.of(new SolverAllocation("1", 5))));
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
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
        RecurringEventResponseDto event = recurringEvent(1L);
        RecurringEventResponseDto foreignEvent = recurringEvent(99L);
        LocalDate date = futureDate(1);
        OccurrenceSlotDto occ = occurrenceSlot(10L, event, date, OccurrenceStatus.SCHEDULED);
        OccurrenceSlotDto foreignSlot = occurrenceSlot(50L, foreignEvent, date, OccurrenceStatus.ASSIGNED);
        Allocation foreignAllocation = allocation(500L, 50L, 5);

        when(solverService.getPreview("prev_confirm")).thenReturn(new SolverPreview("prev_confirm",
                List.of(new SolverAllocation("1", 5))));
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));
        when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any()))
                .thenReturn(List.of(occ));
        when(occurrenceService.findSlotsByStatusBetween(any(), any(), any())).thenReturn(List.of(foreignSlot));
        when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of(foreignAllocation));

        assertThatThrownBy(() -> service.confirm("prev_confirm",
                new ConfirmAutoPreviewRequestDto(List.of(new PreviewAllocationDto(1L, 5)))))
                .isInstanceOf(ReassignConflictException.class);

        verify(allocationRepository, never()).save(any());
        verify(occurrenceService, never()).markAssigned(any());
        verify(solverService, never()).invalidatePreview(any());
    }

    @Test
    @DisplayName("confirm: 409 si dos ítems del propio set chocan entre sí (misma aula, mismo horario, fecha compartida)")
    void confirmConflictoInternoEntreDosItems() {
        RecurringEventResponseDto event1 = recurringEvent(1L);
        RecurringEventResponseDto event2 = recurringEvent(2L);
        LocalDate date = futureDate(1);
        OccurrenceSlotDto occ1 = occurrenceSlot(10L, event1, date, OccurrenceStatus.SCHEDULED);
        OccurrenceSlotDto occ2 = occurrenceSlot(11L, event2, date, OccurrenceStatus.SCHEDULED);

        when(solverService.getPreview("prev_confirm")).thenReturn(new SolverPreview("prev_confirm",
                List.of(new SolverAllocation("1", 9), new SolverAllocation("2", 9))));
        when(academicEventService.findByIds(any())).thenReturn(List.of(event1, event2));
        when(occurrenceService.findSlotsByEventsAndStatuses(any(), any(), any()))
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

    private RecurringEventResponseDto recurringEvent(long id) {
        return recurringEvent(id, LocalTime.of(8, 0), Duration.ofMinutes(90));
    }

    private RecurringEventResponseDto recurringEvent(long id, LocalTime startTime, Duration duration) {
        return new RecurringEventResponseDto(id, EventType.RECURRING, 30, startTime, duration.toMinutes(),
                DayOfWeek.MONDAY, LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(4), null, null);
    }

    private OccurrenceSlotDto occurrenceSlot(long id, RecurringEventResponseDto event, LocalDate date, OccurrenceStatus status) {
        return new OccurrenceSlotDto(id, event.id(), date, event.startTime(), event.endTime(), status, event.enrolled());
    }

    private Allocation allocation(long id, Long occurrenceId, Integer classroomId) {
        return Allocation.builder()
                .id(id)
                .occurrenceId(occurrenceId)
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
