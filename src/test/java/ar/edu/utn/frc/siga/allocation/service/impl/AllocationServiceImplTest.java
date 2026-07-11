package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.request.AllocateFromDateRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocateOccurrenceRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.BatchReassignRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.exception.ReassignConflictException;
import ar.edu.utn.frc.siga.allocation.mapper.AllocationComposer;
import ar.edu.utn.frc.siga.allocation.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AllocationServiceImpl")
class AllocationServiceImplTest {

    @Mock
    private AllocationRepository allocationRepository;
    @Mock
    private OccurrenceRepository occurrenceRepository;
    @Mock
    private AcademicEventRepository eventRepository;
    @Mock
    private ClassroomService classroomService;
    @Mock
    private AllocationComposer composer;

    private AllocationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AllocationServiceImpl(allocationRepository, occurrenceRepository, eventRepository, classroomService, composer);

        lenient().when(classroomService.findById(any())).thenAnswer(invocation -> classroom(invocation.getArgument(0), 100));
        lenient().when(allocationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(composer.compose(any())).thenReturn(dummyResponseDto());
        lenient().when(composer.composeAll(any())).thenAnswer(invocation -> {
            List<Allocation> allocations = invocation.getArgument(0);
            return allocations.stream().map(a -> dummyResponseDto()).toList();
        });
        lenient().when(allocationRepository.findOccupancyBetween(any(), any(), eq(OccurrenceStatus.ASSIGNED)))
                .thenReturn(List.of());
        lenient().when(allocationRepository.findByOccurrence_Id(any())).thenReturn(Optional.empty());
    }

    // ---------- assignManually ----------

    @Test
    @DisplayName("assignManually: aula libre, crea la asignación")
    void assignManuallyAulaLibre() {
        RecurringEvent event = recurringEvent(1L);
        Occurrence occurrence = occurrence(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED);
        when(occurrenceRepository.findById(10L)).thenReturn(Optional.of(occurrence));

        AllocationResponseDto result = service.assignManually(10L, new AllocateOccurrenceRequestDto(5, "obs"));

        assertThat(result).isNotNull();
        verify(allocationRepository).save(org.mockito.ArgumentMatchers.argThat(
                a -> a.getClassroomId().equals(5) && a.getOccurrence().equals(occurrence)));
        assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.ASSIGNED);
    }

    @Test
    @DisplayName("assignManually: aula ocupada por otro evento en la misma franja horaria → 409, no persiste")
    void assignManuallyConSolape() {
        RecurringEvent event = recurringEvent(1L);
        Occurrence occurrence = occurrence(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED);
        RecurringEvent foreignEvent = recurringEvent(99L);
        Allocation foreignAllocation = allocation(500L,
                occurrence(50L, foreignEvent, futureDate(1), OccurrenceStatus.ASSIGNED), 5);

        when(occurrenceRepository.findById(10L)).thenReturn(Optional.of(occurrence));
        when(allocationRepository.findOccupancyBetween(any(), any(), eq(OccurrenceStatus.ASSIGNED)))
                .thenReturn(List.of(foreignAllocation));

        assertThatThrownBy(() -> service.assignManually(10L, new AllocateOccurrenceRequestDto(5, null)))
                .isInstanceOf(ReassignConflictException.class)
                .satisfies(ex -> assertThat(((ReassignConflictException) ex).getConflicts()).hasSize(1));

        verify(allocationRepository, never()).save(any());
        verify(occurrenceRepository, never()).save(any());
    }

    @Test
    @DisplayName("assignManually: franjas adyacentes (fin del nuevo == inicio del ocupante) no conflictúan")
    void assignManuallyFranjasAdyacentesNoConflictuan() {
        RecurringEvent event = recurringEvent(1L); // 08:00-09:30
        Occurrence occurrence = occurrence(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED);
        RecurringEvent adjacentEvent = recurringEvent(99L, LocalTime.of(9, 30), Duration.ofMinutes(60)); // 09:30-10:30
        Allocation adjacentAllocation = allocation(700L,
                occurrence(70L, adjacentEvent, futureDate(1), OccurrenceStatus.ASSIGNED), 5);

        when(occurrenceRepository.findById(10L)).thenReturn(Optional.of(occurrence));
        when(allocationRepository.findOccupancyBetween(any(), any(), eq(OccurrenceStatus.ASSIGNED)))
                .thenReturn(List.of(adjacentAllocation));

        AllocationResponseDto result = service.assignManually(10L, new AllocateOccurrenceRequestDto(5, null));

        assertThat(result).isNotNull();
        verify(allocationRepository).save(any());
    }

    // ---------- reassign ----------

    @Test
    @DisplayName("reassign: aula ocupada por otro evento en la misma franja → 409, no persiste")
    void reassignConSolape() {
        RecurringEvent event = recurringEvent(1L);
        Occurrence occurrence = occurrence(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED);
        Allocation allocation = allocation(900L, occurrence, 3);
        RecurringEvent foreignEvent = recurringEvent(99L);
        Allocation foreignAllocation = allocation(500L,
                occurrence(50L, foreignEvent, futureDate(1), OccurrenceStatus.ASSIGNED), 5);

        when(allocationRepository.findById(900L)).thenReturn(Optional.of(allocation));
        when(allocationRepository.findOccupancyBetween(any(), any(), eq(OccurrenceStatus.ASSIGNED)))
                .thenReturn(List.of(foreignAllocation));

        assertThatThrownBy(() -> service.reassign(900L, new AllocateOccurrenceRequestDto(5, null)))
                .isInstanceOf(ReassignConflictException.class);

        verify(allocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("reassign: la aula que ya tenía la propia asignación no conflictúa consigo misma")
    void reassignMismaAulaNoConflictuaConsigoMisma() {
        RecurringEvent event = recurringEvent(1L);
        Occurrence occurrence = occurrence(10L, event, futureDate(1), OccurrenceStatus.ASSIGNED);
        Allocation allocation = allocation(900L, occurrence, 5);

        when(allocationRepository.findById(900L)).thenReturn(Optional.of(allocation));
        // La ocupación de BD trae la propia allocation (misma occurrence) en el aula 5.
        when(allocationRepository.findOccupancyBetween(any(), any(), eq(OccurrenceStatus.ASSIGNED)))
                .thenReturn(List.of(allocation));

        AllocationResponseDto result = service.reassign(900L, new AllocateOccurrenceRequestDto(5, "misma aula"));

        assertThat(result).isNotNull();
        verify(allocationRepository).save(allocation);
        assertThat(allocation.getClassroomId()).isEqualTo(5);
    }

    // ---------- batchReassign ----------

    @Test
    @DisplayName("batchReassign: dos moves a aulas libres, ambos se aplican")
    void batchReassignFeliz() {
        RecurringEvent event1 = recurringEvent(1L);
        RecurringEvent event2 = recurringEvent(2L, LocalTime.of(14, 0), Duration.ofMinutes(60));
        Occurrence occ1 = occurrence(10L, event1, futureDate(1), OccurrenceStatus.ASSIGNED);
        Occurrence occ2 = occurrence(11L, event2, futureDate(2), OccurrenceStatus.ASSIGNED);
        Allocation alloc1 = allocation(100L, occ1, 3);
        Allocation alloc2 = allocation(101L, occ2, 4);

        when(allocationRepository.findById(100L)).thenReturn(Optional.of(alloc1));
        when(allocationRepository.findById(101L)).thenReturn(Optional.of(alloc2));
        // Las propias allocations del batch aparecen en la ocupación de BD (ocupan su aula
        // actual): deben excluirse porque justamente se están moviendo en esta operación.
        when(allocationRepository.findOccupancyBetween(any(), any(), eq(OccurrenceStatus.ASSIGNED)))
                .thenReturn(List.of(alloc1, alloc2));

        List<AllocationResponseDto> results = service.batchReassign(new BatchReassignRequestDto(
                List.of(new BatchReassignRequestDto.MoveDto(100L, 5), new BatchReassignRequestDto.MoveDto(101L, 6))));

        assertThat(results).hasSize(2);
        verify(allocationRepository, times(2)).save(any());
        assertThat(alloc1.getClassroomId()).isEqualTo(5);
        assertThat(alloc2.getClassroomId()).isEqualTo(6);
    }

    @Test
    @DisplayName("batchReassign: un move choca contra ocupación firme de BD ajena al lote → 409 sin ningún save")
    void batchReassignConflictoContraBd() {
        RecurringEvent event1 = recurringEvent(1L);
        Occurrence occ1 = occurrence(10L, event1, futureDate(1), OccurrenceStatus.SCHEDULED);
        Allocation alloc1 = allocation(100L, occ1, 3);

        RecurringEvent foreignEvent = recurringEvent(99L);
        Allocation foreignAllocation = allocation(500L,
                occurrence(50L, foreignEvent, futureDate(1), OccurrenceStatus.ASSIGNED), 5);

        when(allocationRepository.findById(100L)).thenReturn(Optional.of(alloc1));
        when(allocationRepository.findOccupancyBetween(any(), any(), eq(OccurrenceStatus.ASSIGNED)))
                .thenReturn(List.of(foreignAllocation));

        assertThatThrownBy(() -> service.batchReassign(new BatchReassignRequestDto(
                List.of(new BatchReassignRequestDto.MoveDto(100L, 5)))))
                .isInstanceOf(ReassignConflictException.class);

        verify(allocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("batchReassign: dos moves del propio lote chocan entre sí (misma aula, misma fecha, franjas que se pisan) → 409")
    void batchReassignConflictoInternoEntreDosMoves() {
        RecurringEvent event1 = recurringEvent(1L);
        RecurringEvent event2 = recurringEvent(2L);
        LocalDate date = futureDate(1);
        Occurrence occ1 = occurrence(10L, event1, date, OccurrenceStatus.SCHEDULED);
        Occurrence occ2 = occurrence(11L, event2, date, OccurrenceStatus.SCHEDULED);
        Allocation alloc1 = allocation(100L, occ1, 3);
        Allocation alloc2 = allocation(101L, occ2, 4);

        when(allocationRepository.findById(100L)).thenReturn(Optional.of(alloc1));
        when(allocationRepository.findById(101L)).thenReturn(Optional.of(alloc2));

        assertThatThrownBy(() -> service.batchReassign(new BatchReassignRequestDto(
                List.of(new BatchReassignRequestDto.MoveDto(100L, 9), new BatchReassignRequestDto.MoveDto(101L, 9)))))
                .isInstanceOf(ReassignConflictException.class);

        verify(allocationRepository, never()).save(any());
    }

    // ---------- assignManuallyFromDate (comportamiento previo intacto) ----------

    @Test
    @DisplayName("assignManuallyFromDate: aula libre para todas las ocurrencias futuras, se asignan")
    void assignManuallyFromDateFeliz() {
        RecurringEvent event = recurringEvent(1L);
        LocalDate date1 = futureDate(1);
        LocalDate date2 = futureDate(8);
        Occurrence occ1 = occurrence(10L, event, date1, OccurrenceStatus.SCHEDULED);
        Occurrence occ2 = occurrence(11L, event, date2, OccurrenceStatus.SCHEDULED);

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(occurrenceRepository.findByEvent_IdAndDateGreaterThanEqual(eq(1L), any()))
                .thenReturn(List.of(occ1, occ2));

        List<AllocationResponseDto> results = service.assignManuallyFromDate(
                new AllocateFromDateRequestDto(1L, date1, 5, "obs"));

        assertThat(results).hasSize(2);
        verify(allocationRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("assignManuallyFromDate: una ocurrencia futura choca contra otro evento → 409, nada se aplica")
    void assignManuallyFromDateConSolape() {
        RecurringEvent event = recurringEvent(1L);
        LocalDate date1 = futureDate(1);
        Occurrence occ1 = occurrence(10L, event, date1, OccurrenceStatus.SCHEDULED);

        RecurringEvent foreignEvent = recurringEvent(99L);
        Allocation foreignAllocation = allocation(500L,
                occurrence(50L, foreignEvent, date1, OccurrenceStatus.ASSIGNED), 5);

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(occurrenceRepository.findByEvent_IdAndDateGreaterThanEqual(eq(1L), any()))
                .thenReturn(List.of(occ1));
        when(allocationRepository.findOccupancyBetween(any(), any(), eq(OccurrenceStatus.ASSIGNED)))
                .thenReturn(List.of(foreignAllocation));

        assertThatThrownBy(() -> service.assignManuallyFromDate(
                new AllocateFromDateRequestDto(1L, date1, 5, null)))
                .isInstanceOf(ReassignConflictException.class);

        verify(allocationRepository, never()).save(any());
        verify(occurrenceRepository, never()).save(any());
    }

    // ---------- helpers ----------

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

    private AllocationResponseDto dummyResponseDto() {
        return new AllocationResponseDto(1L, AllocationSource.MANUAL, LocalDateTime.now(), null, null, null, null);
    }

    private LocalDate futureDate(int daysFromNow) {
        return LocalDate.now().plusDays(daysFromNow);
    }
}
