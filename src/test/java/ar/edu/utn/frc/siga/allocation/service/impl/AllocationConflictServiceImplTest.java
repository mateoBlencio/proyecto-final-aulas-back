package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationConflictDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OverlapConflictDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OvercrowdedConflictDto;
import ar.edu.utn.frc.siga.allocation.dto.response.UnallocatedConflictDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.model.ConflictType;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.common.exception.InvalidDateRangeException;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AllocationConflictServiceImpl")
class AllocationConflictServiceImplTest {

    private static final Pageable PAGEABLE = PageRequest.of(0, 20);

    @Mock
    private ClassroomService classroomService;
    @Mock
    private AcademicEventService academicEventService;
    @Mock
    private AcademicPeriodService academicPeriodService;
    @Mock
    private AllocationOccupancyReader occupancyReader;
    @Mock
    private OccurrenceService occurrenceService;
    @Mock
    private AllocationRepository allocationRepository;

    @InjectMocks
    private AllocationConflictServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(occupancyReader.loadAllocated(any(), any())).thenReturn(List.of());
        lenient().when(academicEventService.findByIds(any())).thenReturn(List.of());
        lenient().when(academicPeriodService.findActive()).thenReturn(List.of());
        lenient().when(classroomService.findByIds(any())).thenReturn(List.of());
        lenient().when(occurrenceService.findSlotsByStatusBetween(any(), any(), any())).thenReturn(List.of());
        lenient().when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("types vacío devuelve los tres tipos de conflicto")
    void typesVacioDevuelveLosTresTipos() {
        LocalDate from = futureDate(0);
        LocalDate to = futureDate(30);
        RecurringEventResponseDto event = recurringEvent(1L, 40, LocalTime.of(8, 0));
        OccurrenceSlotDto slot = occurrenceSlot(10L, event, futureDate(2));
        Allocation allocation = allocation(100L, 10L, 5);
        mockOccupancy(List.of(slot), List.of(allocation), List.of(event));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, 30)));

        List<AllocationConflictDto> result = service.findConflicts(Set.of(), from, to, false, PAGEABLE).getContent();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isInstanceOf(OvercrowdedConflictDto.class);
    }

    @Test
    @DisplayName("Detecta sobrecupo cuando los inscriptos superan la capacidad del aula")
    void detectaSobrecupo() {
        LocalDate from = futureDate(0);
        LocalDate to = futureDate(30);
        RecurringEventResponseDto event = recurringEvent(1L, 40, LocalTime.of(8, 0));
        OccurrenceSlotDto slot = occurrenceSlot(10L, event, futureDate(2));
        Allocation allocation = allocation(100L, 10L, 5);
        mockOccupancy(List.of(slot), List.of(allocation), List.of(event));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, 30)));

        List<AllocationConflictDto> result =
                service.findConflicts(Set.of(ConflictType.OVERCROWDED), from, to, false, PAGEABLE).getContent();

        assertThat(result).hasSize(1);
        OvercrowdedConflictDto overcrowded = (OvercrowdedConflictDto) result.getFirst();
        assertThat(overcrowded.enrolled()).isEqualTo(40);
        assertThat(overcrowded.capacity()).isEqualTo(30);
        assertThat(overcrowded.overcrowdedBy()).isEqualTo(10);
        assertThat(overcrowded.dates()).containsExactly(futureDate(2));
    }

    @Test
    @DisplayName("No detecta sobrecupo cuando el aula no tiene capacidad informada")
    void noDetectaSobrecupoSinCapacidad() {
        LocalDate from = futureDate(0);
        LocalDate to = futureDate(30);
        RecurringEventResponseDto event = recurringEvent(1L, 40, LocalTime.of(8, 0));
        OccurrenceSlotDto slot = occurrenceSlot(10L, event, futureDate(2));
        Allocation allocation = allocation(100L, 10L, 5);
        mockOccupancy(List.of(slot), List.of(allocation), List.of(event));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, null)));

        assertThat(service.findConflicts(Set.of(ConflictType.OVERCROWDED), from, to, false, PAGEABLE)).isEmpty();
    }

    @Test
    @DisplayName("No detecta sobrecupo cuando 'enrolled' es null (se trata como 0)")
    void noDetectaSobrecupoConEnrolledNull() {
        LocalDate from = futureDate(0);
        LocalDate to = futureDate(30);
        RecurringEventResponseDto event = recurringEvent(1L, null, LocalTime.of(8, 0));
        OccurrenceSlotDto slot = occurrenceSlot(10L, event, futureDate(2));
        Allocation allocation = allocation(100L, 10L, 5);
        mockOccupancy(List.of(slot), List.of(allocation), List.of(event));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, 30)));

        assertThat(service.findConflicts(Set.of(ConflictType.OVERCROWDED), from, to, false, PAGEABLE)).isEmpty();
    }

    @Test
    @DisplayName("Detecta superposición cuando dos eventos comparten aula y fecha con horarios cruzados")
    void detectaSolapeMismaAulaMismaFecha() {
        LocalDate from = futureDate(0);
        LocalDate to = futureDate(30);
        LocalDate date = futureDate(2);
        RecurringEventResponseDto eventA = recurringEvent(1L, 10, LocalTime.of(8, 0));
        RecurringEventResponseDto eventB = recurringEvent(2L, 10, LocalTime.of(8, 30));
        OccurrenceSlotDto slotA = occurrenceSlot(10L, eventA, date);
        OccurrenceSlotDto slotB = occurrenceSlot(11L, eventB, date);
        Allocation allocA = allocation(100L, 10L, 5);
        Allocation allocB = allocation(101L, 11L, 5);
        mockOccupancy(List.of(slotA, slotB), List.of(allocA, allocB), List.of(eventA, eventB));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, 100)));

        List<AllocationConflictDto> result =
                service.findConflicts(Set.of(ConflictType.OVERLAP), from, to, false, PAGEABLE).getContent();

        assertThat(result).hasSize(1);
        OverlapConflictDto overlap = (OverlapConflictDto) result.getFirst();
        assertThat(overlap.classroom().id()).isEqualTo(5);
        assertThat(overlap.eventA().id()).isEqualTo(1L);
        assertThat(overlap.eventB().id()).isEqualTo(2L);
        assertThat(overlap.dates()).containsExactly(date);
    }

    @Test
    @DisplayName("No detecta superposición cuando los eventos están en aulas distintas")
    void noDetectaSolapeEnAulasDistintas() {
        LocalDate from = futureDate(0);
        LocalDate to = futureDate(30);
        LocalDate date = futureDate(2);
        RecurringEventResponseDto eventA = recurringEvent(1L, 10, LocalTime.of(8, 0));
        RecurringEventResponseDto eventB = recurringEvent(2L, 10, LocalTime.of(8, 30));
        OccurrenceSlotDto slotA = occurrenceSlot(10L, eventA, date);
        OccurrenceSlotDto slotB = occurrenceSlot(11L, eventB, date);
        Allocation allocA = allocation(100L, 10L, 5);
        Allocation allocB = allocation(101L, 11L, 6);
        mockOccupancy(List.of(slotA, slotB), List.of(allocA, allocB), List.of(eventA, eventB));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, 100), classroom(6, 100)));

        assertThat(service.findConflicts(Set.of(ConflictType.OVERLAP), from, to, false, PAGEABLE)).isEmpty();
    }

    @Test
    @DisplayName("No detecta superposición cuando las franjas son adyacentes (fin de una = inicio de la otra)")
    void noDetectaSolapeEnFranjasAdyacentes() {
        LocalDate from = futureDate(0);
        LocalDate to = futureDate(30);
        LocalDate date = futureDate(2);
        RecurringEventResponseDto eventA = recurringEvent(1L, 10, LocalTime.of(8, 0));
        RecurringEventResponseDto eventB = recurringEvent(2L, 10, LocalTime.of(9, 0));
        OccurrenceSlotDto slotA = occurrenceSlot(10L, eventA, date);
        OccurrenceSlotDto slotB = occurrenceSlot(11L, eventB, date);
        Allocation allocA = allocation(100L, 10L, 5);
        Allocation allocB = allocation(101L, 11L, 5);
        mockOccupancy(List.of(slotA, slotB), List.of(allocA, allocB), List.of(eventA, eventB));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, 100)));

        assertThat(service.findConflicts(Set.of(ConflictType.OVERLAP), from, to, false, PAGEABLE)).isEmpty();
    }

    @Test
    @DisplayName("Agrega todas las fechas de un par recurrente en conflicto en una sola fila")
    void agregaFechasDeParRecurrenteEnUnaFila() {
        LocalDate from = futureDate(0);
        LocalDate to = futureDate(30);
        LocalDate date1 = futureDate(2);
        LocalDate date2 = futureDate(9);
        RecurringEventResponseDto eventA = recurringEvent(1L, 10, LocalTime.of(8, 0));
        RecurringEventResponseDto eventB = recurringEvent(2L, 10, LocalTime.of(8, 30));

        OccurrenceSlotDto slotA1 = occurrenceSlot(10L, eventA, date1);
        OccurrenceSlotDto slotB1 = occurrenceSlot(11L, eventB, date1);
        OccurrenceSlotDto slotA2 = occurrenceSlot(12L, eventA, date2);
        OccurrenceSlotDto slotB2 = occurrenceSlot(13L, eventB, date2);
        Allocation allocA1 = allocation(100L, 10L, 5);
        Allocation allocB1 = allocation(101L, 11L, 5);
        Allocation allocA2 = allocation(102L, 12L, 5);
        Allocation allocB2 = allocation(103L, 13L, 5);
        mockOccupancy(List.of(slotA1, slotB1, slotA2, slotB2),
                List.of(allocA1, allocB1, allocA2, allocB2), List.of(eventA, eventB));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, 100)));

        List<AllocationConflictDto> result =
                service.findConflicts(Set.of(ConflictType.OVERLAP), from, to, false, PAGEABLE).getContent();

        assertThat(result).hasSize(1);
        assertThat(((OverlapConflictDto) result.getFirst()).dates()).containsExactly(date1, date2);
    }

    @Test
    @DisplayName("Detecta ocurrencia sin aula: NEEDS_ROOM sin fila de asignación")
    void detectaSinAula() {
        LocalDate from = futureDate(0);
        LocalDate to = futureDate(30);
        RecurringEventResponseDto event = recurringEvent(1L, 10, LocalTime.of(8, 0));
        OccurrenceSlotDto slot = occurrenceSlot(10L, event, futureDate(2));
        when(occurrenceService.findSlotsByStatusBetween(eq(OccurrenceStatus.NEEDS_ROOM), any(), any()))
                .thenReturn(List.of(slot));
        when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of());
        when(academicEventService.findByIds(any())).thenReturn(List.of(event));

        List<AllocationConflictDto> result =
                service.findConflicts(Set.of(ConflictType.UNALLOCATED), from, to, false, PAGEABLE).getContent();

        assertThat(result).hasSize(1);
        assertThat(((UnallocatedConflictDto) result.getFirst()).event().id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Una ocurrencia NEEDS_ROOM con fila de asignación no cuenta como sin aula (anti-join)")
    void noDetectaSinAulaSiTieneAllocation() {
        LocalDate from = futureDate(0);
        LocalDate to = futureDate(30);
        RecurringEventResponseDto event = recurringEvent(1L, 10, LocalTime.of(8, 0));
        OccurrenceSlotDto slot = occurrenceSlot(10L, event, futureDate(2));
        when(occurrenceService.findSlotsByStatusBetween(eq(OccurrenceStatus.NEEDS_ROOM), any(), any()))
                .thenReturn(List.of(slot));
        when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of(allocation(100L, 10L, 5)));

        assertThat(service.findConflicts(Set.of(ConflictType.UNALLOCATED), from, to, false, PAGEABLE)).isEmpty();
    }

    @Test
    @DisplayName("Usa el fin del período académico activo como 'to' por defecto")
    void usaPeriodoActivoComoToPorDefecto() {
        LocalDate endDate = LocalDate.now().plusDays(50);
        when(academicPeriodService.findActive()).thenReturn(List.of(
                new AcademicPeriodResponseDto(2026, 2, LocalDate.now().minusDays(10), endDate)));

        service.findConflicts(Set.of(ConflictType.OVERCROWDED), null, null, false, PAGEABLE);

        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(occupancyReader).loadAllocated(any(), toCaptor.capture());
        assertThat(toCaptor.getValue()).isEqualTo(endDate);
    }

    @Test
    @DisplayName("Usa 'from' + 6 meses como fallback cuando no hay período académico activo")
    void usaFallbackSeisMesesSinPeriodoActivo() {
        LocalDate from = LocalDate.of(2026, 8, 1);

        service.findConflicts(Set.of(ConflictType.OVERCROWDED), from, null, false, PAGEABLE);

        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(occupancyReader).loadAllocated(eq(from), toCaptor.capture());
        assertThat(toCaptor.getValue()).isEqualTo(from.plusMonths(6));
    }

    @Test
    @DisplayName("Lanza InvalidDateRangeException cuando 'to' es anterior a 'from'")
    void lanzaExcepcionCuandoToEsAnteriorAFrom() {
        LocalDate from = LocalDate.of(2026, 8, 10);
        LocalDate to = LocalDate.of(2026, 8, 1);

        assertThatThrownBy(() -> service.findConflicts(Set.of(ConflictType.OVERCROWDED), from, to, false, PAGEABLE))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    @DisplayName("resolveAllUnallocatedEventIds delega en unallocatedEventIds con el rango por defecto")
    void resolveAllUnallocatedEventIdsDelegaConRangoPorDefecto() {
        RecurringEventResponseDto event = recurringEvent(1L, 10, LocalTime.of(8, 0));
        OccurrenceSlotDto slot = occurrenceSlot(10L, event, futureDate(2));
        when(occurrenceService.findSlotsByStatusBetween(eq(OccurrenceStatus.NEEDS_ROOM), any(), any()))
                .thenReturn(List.of(slot));
        when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of());

        List<Long> ids = service.resolveAllUnallocatedEventIds();

        assertThat(ids).containsExactly(1L);
    }

    private void mockOccupancy(List<OccurrenceSlotDto> slots, List<Allocation> allocations,
            List<RecurringEventResponseDto> events) {
        List<OccupiedSlot> occupied = allocations.stream()
                .map(a -> OccupiedSlot.from(a, slots.stream()
                        .filter(s -> s.occurrenceId().equals(a.getOccurrenceId())).findFirst().orElseThrow()))
                .toList();
        lenient().when(occupancyReader.loadAllocated(any(), any())).thenReturn(occupied);
        when(academicEventService.findByIds(any())).thenReturn(List.copyOf(events));
    }

    /** Fecha relativa a hoy: evita que los tests de solapamiento/sobrecupo (isPast() filtra por reloj real) rompan con el paso del calendario. */
    private LocalDate futureDate(int daysFromNow) {
        return LocalDate.now().plusDays(daysFromNow);
    }

    private RecurringEventResponseDto recurringEvent(long id, Integer enrolled, LocalTime startTime) {
        return new RecurringEventResponseDto(id, EventType.RECURRING, enrolled, startTime, 60,
                DayOfWeek.MONDAY, LocalDate.of(2026, 1, 1), null, null, null);
    }

    private OccurrenceSlotDto occurrenceSlot(long id, RecurringEventResponseDto event, LocalDate date) {
        return new OccurrenceSlotDto(id, event.id(), date, event.startTime(), event.endTime(),
                OccurrenceStatus.NEEDS_ROOM, event.enrolled());
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
        return new ClassroomResponseDto(id, "Aula " + id, 1, capacity, true, 1, "Edificio 1", 1, "Tipo");
    }
}
