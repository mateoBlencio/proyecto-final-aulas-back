package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.request.AllocateFromDateRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocateOccurrenceRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.BatchReassignRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.exception.ReassignConflictException;
import ar.edu.utn.frc.siga.allocation.mapper.AllocationComposer;
import ar.edu.utn.frc.siga.allocation.events.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.events.model.UniqueEvent;
import ar.edu.utn.frc.siga.allocation.events.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.allocation.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.argThat;
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
    private OccurrenceService occurrenceService;
    @Mock
    private AcademicEventRepository eventRepository;
    @Mock
    private ClassroomService classroomService;
    @Mock
    private AllocationComposer composer;

    private AllocationServiceImpl service;

    @BeforeEach
    void setUp() {
        AllocationValidator validator = new AllocationValidator(classroomService, allocationRepository, occurrenceService);
        AllocationWriter writer = new AllocationWriter(allocationRepository, validator, occurrenceService);
        service = new AllocationServiceImpl(allocationRepository, occurrenceService, eventRepository, composer, validator, writer);

        lenient().when(classroomService.findByIds(any())).thenAnswer(invocation -> {
            java.util.Collection<Integer> ids = invocation.getArgument(0);
            if (ids == null) return List.of();
            return ids.stream().map(id -> classroom(id, 100)).toList();
        });
        lenient().when(allocationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(composer.compose(any())).thenReturn(dummyResponseDto());
        lenient().when(composer.composeAll(any())).thenAnswer(invocation -> {
            List<Allocation> allocations = invocation.getArgument(0);
            return allocations.stream().map(a -> dummyResponseDto()).toList();
        });
        lenient().when(occurrenceService.findSlotsByStatusBetween(any(), any(), any())).thenReturn(List.of());
        lenient().when(allocationRepository.findByOccurrenceId(any())).thenReturn(Optional.empty());
        lenient().when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of());
    }

    // ---------- allocateManually ----------

    @Test
    @DisplayName("allocateManually: aula libre, crea la asignación")
    void allocateManuallyAulaLibre() {
        RecurringEvent event = recurringEvent(1L);
        OccurrenceSlotDto occurrence = occurrenceSlot(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED);
        when(occurrenceService.findSlot(10L)).thenReturn(occurrence);

        AllocationResponseDto result = service.allocateManually(10L, new AllocateOccurrenceRequestDto(5, "obs"));

        assertThat(result).isNotNull();
        verify(allocationRepository).save(argThat(
                a -> a.getClassroomId().equals(5) && a.getOccurrenceId().equals(10L)
                        && a.getSource() == AllocationSource.MANUAL));
        verify(occurrenceService).markAssigned(List.of(10L));
    }

    @Test
    @DisplayName("allocateManually: ocurrencia ya pasada → conflicto, no persiste")
    void allocateManuallyOcurrenciaPasada() {
        RecurringEvent event = recurringEvent(1L);
        OccurrenceSlotDto occurrence = occurrenceSlot(10L, event, LocalDate.now().minusDays(1), OccurrenceStatus.SCHEDULED);
        when(occurrenceService.findSlot(10L)).thenReturn(occurrence);

        assertThatThrownBy(() -> service.allocateManually(10L, new AllocateOccurrenceRequestDto(5, null)))
                .isInstanceOf(AllocationConflictException.class);

        verify(allocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("allocateManually: ocurrencia CANCELLED o SUSPENDED → conflicto, no persiste")
    void allocateManuallyOcurrenciaNoAsignable() {
        RecurringEvent event = recurringEvent(1L);
        OccurrenceSlotDto cancelled = occurrenceSlot(10L, event, futureDate(1), OccurrenceStatus.CANCELLED);
        OccurrenceSlotDto suspended = occurrenceSlot(11L, event, futureDate(1), OccurrenceStatus.SUSPENDED);
        when(occurrenceService.findSlot(10L)).thenReturn(cancelled);
        when(occurrenceService.findSlot(11L)).thenReturn(suspended);

        assertThatThrownBy(() -> service.allocateManually(10L, new AllocateOccurrenceRequestDto(5, null)))
                .isInstanceOf(AllocationConflictException.class);
        assertThatThrownBy(() -> service.allocateManually(11L, new AllocateOccurrenceRequestDto(5, null)))
                .isInstanceOf(AllocationConflictException.class);

        verify(allocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("allocateManually: ocurrencia que ya tiene asignación → conflicto, no persiste")
    void allocateManuallyOcurrenciaYaAsignada() {
        RecurringEvent event = recurringEvent(1L);
        OccurrenceSlotDto occurrence = occurrenceSlot(10L, event, futureDate(1), OccurrenceStatus.ASSIGNED);
        when(occurrenceService.findSlot(10L)).thenReturn(occurrence);
        when(allocationRepository.findByOccurrenceId(10L))
                .thenReturn(Optional.of(allocation(999L, occurrence, 3)));

        assertThatThrownBy(() -> service.allocateManually(10L, new AllocateOccurrenceRequestDto(5, null)))
                .isInstanceOf(AllocationConflictException.class);

        verify(allocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("allocateManually: aula inexistente → conflicto 409")
    void allocateManuallyAulaInexistente() {
        RecurringEvent event = recurringEvent(1L);
        OccurrenceSlotDto occurrence = occurrenceSlot(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED);
        when(occurrenceService.findSlot(10L)).thenReturn(occurrence);
        when(classroomService.findByIds(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.allocateManually(10L, new AllocateOccurrenceRequestDto(999, null)))
                .isInstanceOf(AllocationConflictException.class);

        verify(allocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("allocateManually: aula existe pero no está disponible → 409 (cierra el FIXME: antes solo se validaba existencia)")
    void allocateManuallyAulaNoDisponibleRechaza() {
        RecurringEvent event = recurringEvent(1L);
        OccurrenceSlotDto occurrence = occurrenceSlot(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED);
        when(occurrenceService.findSlot(10L)).thenReturn(occurrence);
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, 100, false)));

        assertThatThrownBy(() -> service.allocateManually(10L, new AllocateOccurrenceRequestDto(5, null)))
                .isInstanceOf(AllocationConflictException.class);

        verify(allocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("allocateManually: aula ocupada por otro evento en la misma franja horaria → 409, no persiste")
    void allocateManuallyConSolape() {
        RecurringEvent event = recurringEvent(1L);
        OccurrenceSlotDto occurrence = occurrenceSlot(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED);
        RecurringEvent foreignEvent = recurringEvent(99L);
        OccurrenceSlotDto foreignOccurrence = occurrenceSlot(50L, foreignEvent, futureDate(1), OccurrenceStatus.ASSIGNED);
        Allocation foreignAllocation = allocation(500L, foreignOccurrence, 5);

        when(occurrenceService.findSlot(10L)).thenReturn(occurrence);
        when(occurrenceService.findSlotsByStatusBetween(any(), any(), any())).thenReturn(List.of(foreignOccurrence));
        when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of(foreignAllocation));

        assertThatThrownBy(() -> service.allocateManually(10L, new AllocateOccurrenceRequestDto(5, null)))
                .isInstanceOf(ReassignConflictException.class)
                .satisfies(ex -> assertThat(((ReassignConflictException) ex).getConflicts()).hasSize(1));

        verify(allocationRepository, never()).save(any());
        verify(occurrenceService, never()).markAssigned(any());
    }

    @Test
    @DisplayName("allocateManually: franjas adyacentes (fin del nuevo == inicio del ocupante) no conflictúan")
    void allocateManuallyFranjasAdyacentesNoConflictuan() {
        RecurringEvent event = recurringEvent(1L); // 08:00-09:30
        OccurrenceSlotDto occurrence = occurrenceSlot(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED);
        RecurringEvent adjacentEvent = recurringEvent(99L, LocalTime.of(9, 30), Duration.ofMinutes(60)); // 09:30-10:30
        OccurrenceSlotDto adjacentOccurrence = occurrenceSlot(70L, adjacentEvent, futureDate(1), OccurrenceStatus.ASSIGNED);
        Allocation adjacentAllocation = allocation(700L, adjacentOccurrence, 5);

        when(occurrenceService.findSlot(10L)).thenReturn(occurrence);
        when(occurrenceService.findSlotsByStatusBetween(any(), any(), any())).thenReturn(List.of(adjacentOccurrence));
        when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of(adjacentAllocation));

        AllocationResponseDto result = service.allocateManually(10L, new AllocateOccurrenceRequestDto(5, null));

        assertThat(result).isNotNull();
        verify(allocationRepository).save(any());
    }

    // ---------- reallocate ----------

    @Test
    @DisplayName("reallocate: aula ocupada por otro evento en la misma franja → 409, no persiste")
    void reallocateConSolape() {
        RecurringEvent event = recurringEvent(1L);
        OccurrenceSlotDto occurrence = occurrenceSlot(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED);
        Allocation allocation = allocation(900L, occurrence, 3);
        RecurringEvent foreignEvent = recurringEvent(99L);
        OccurrenceSlotDto foreignOccurrence = occurrenceSlot(50L, foreignEvent, futureDate(1), OccurrenceStatus.ASSIGNED);
        Allocation foreignAllocation = allocation(500L, foreignOccurrence, 5);

        when(allocationRepository.findById(900L)).thenReturn(Optional.of(allocation));
        when(occurrenceService.findSlot(10L)).thenReturn(occurrence);
        when(occurrenceService.findSlotsByStatusBetween(any(), any(), any())).thenReturn(List.of(foreignOccurrence));
        when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of(foreignAllocation));

        assertThatThrownBy(() -> service.reallocate(900L, new AllocateOccurrenceRequestDto(5, null)))
                .isInstanceOf(ReassignConflictException.class);

        verify(allocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("reallocate: la aula que ya tenía la propia asignación no conflictúa consigo misma")
    void reallocateMismaAulaNoConflictuaConsigoMisma() {
        RecurringEvent event = recurringEvent(1L);
        OccurrenceSlotDto occurrence = occurrenceSlot(10L, event, futureDate(1), OccurrenceStatus.ASSIGNED);
        Allocation allocation = allocation(900L, occurrence, 5);

        when(allocationRepository.findById(900L)).thenReturn(Optional.of(allocation));
        when(occurrenceService.findSlot(10L)).thenReturn(occurrence);
        // La ocupación de BD trae la propia allocation (misma occurrence) en el aula 5.
        when(occurrenceService.findSlotsByStatusBetween(any(), any(), any())).thenReturn(List.of(occurrence));
        when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of(allocation));

        AllocationResponseDto result = service.reallocate(900L, new AllocateOccurrenceRequestDto(5, "misma aula"));

        assertThat(result).isNotNull();
        // allocation llega managed; el service ya no llama save() explícito (dirty checking).
        assertThat(allocation.getClassroomId()).isEqualTo(5);
    }

    // ---------- batchReallocate ----------

    @Test
    @DisplayName("batchReallocate: dos moves a aulas libres, ambos se aplican")
    void batchReallocateFeliz() {
        RecurringEvent event1 = recurringEvent(1L);
        RecurringEvent event2 = recurringEvent(2L, LocalTime.of(14, 0), Duration.ofMinutes(60));
        OccurrenceSlotDto occ1 = occurrenceSlot(10L, event1, futureDate(1), OccurrenceStatus.ASSIGNED);
        OccurrenceSlotDto occ2 = occurrenceSlot(11L, event2, futureDate(2), OccurrenceStatus.ASSIGNED);
        Allocation alloc1 = allocation(100L, occ1, 3);
        Allocation alloc2 = allocation(101L, occ2, 4);

        when(allocationRepository.findById(100L)).thenReturn(Optional.of(alloc1));
        when(allocationRepository.findById(101L)).thenReturn(Optional.of(alloc2));
        when(occurrenceService.findSlots(any())).thenReturn(List.of(occ1, occ2));
        // Las propias allocations del batch aparecen en la ocupación de BD (ocupan su aula
        // actual): deben excluirse porque justamente se están moviendo en esta operación.
        when(occurrenceService.findSlotsByStatusBetween(any(), any(), any())).thenReturn(List.of(occ1, occ2));
        when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of(alloc1, alloc2));

        List<AllocationResponseDto> results = service.batchReallocate(new BatchReassignRequestDto(
                List.of(new BatchReassignRequestDto.MoveDto(100L, 5), new BatchReassignRequestDto.MoveDto(101L, 6))));

        assertThat(results).hasSize(2);
        // Ambas allocations llegan managed; el service ya no llama save() explícito (dirty checking).
        assertThat(alloc1.getClassroomId()).isEqualTo(5);
        assertThat(alloc2.getClassroomId()).isEqualTo(6);
    }

    @Test
    @DisplayName("batchReallocate: un move choca contra ocupación firme de BD ajena al lote → 409 sin ningún save")
    void batchReallocateConflictoContraBd() {
        RecurringEvent event1 = recurringEvent(1L);
        OccurrenceSlotDto occ1 = occurrenceSlot(10L, event1, futureDate(1), OccurrenceStatus.SCHEDULED);
        Allocation alloc1 = allocation(100L, occ1, 3);

        RecurringEvent foreignEvent = recurringEvent(99L);
        OccurrenceSlotDto foreignOccurrence = occurrenceSlot(50L, foreignEvent, futureDate(1), OccurrenceStatus.ASSIGNED);
        Allocation foreignAllocation = allocation(500L, foreignOccurrence, 5);

        when(allocationRepository.findById(100L)).thenReturn(Optional.of(alloc1));
        when(occurrenceService.findSlots(any())).thenReturn(List.of(occ1));
        when(occurrenceService.findSlotsByStatusBetween(any(), any(), any())).thenReturn(List.of(foreignOccurrence));
        when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of(foreignAllocation));

        assertThatThrownBy(() -> service.batchReallocate(new BatchReassignRequestDto(
                List.of(new BatchReassignRequestDto.MoveDto(100L, 5)))))
                .isInstanceOf(ReassignConflictException.class);

        verify(allocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("batchReallocate: dos moves del propio lote chocan entre sí (misma aula, misma fecha, franjas que se pisan) → 409")
    void batchReallocateConflictoInternoEntreDosMoves() {
        RecurringEvent event1 = recurringEvent(1L);
        RecurringEvent event2 = recurringEvent(2L);
        LocalDate date = futureDate(1);
        OccurrenceSlotDto occ1 = occurrenceSlot(10L, event1, date, OccurrenceStatus.SCHEDULED);
        OccurrenceSlotDto occ2 = occurrenceSlot(11L, event2, date, OccurrenceStatus.SCHEDULED);
        Allocation alloc1 = allocation(100L, occ1, 3);
        Allocation alloc2 = allocation(101L, occ2, 4);

        when(allocationRepository.findById(100L)).thenReturn(Optional.of(alloc1));
        when(allocationRepository.findById(101L)).thenReturn(Optional.of(alloc2));
        when(occurrenceService.findSlots(any())).thenReturn(List.of(occ1, occ2));

        assertThatThrownBy(() -> service.batchReallocate(new BatchReassignRequestDto(
                List.of(new BatchReassignRequestDto.MoveDto(100L, 9), new BatchReassignRequestDto.MoveDto(101L, 9)))))
                .isInstanceOf(ReassignConflictException.class);

        verify(allocationRepository, never()).save(any());
    }

    // ---------- allocateManuallyFromDate (comportamiento previo intacto) ----------

    @Test
    @DisplayName("allocateManuallyFromDate: aula libre para todas las ocurrencias futuras, se asignan")
    void allocateManuallyFromDateFeliz() {
        RecurringEvent event = recurringEvent(1L);
        LocalDate date1 = futureDate(1);
        LocalDate date2 = futureDate(8);
        OccurrenceSlotDto occ1 = occurrenceSlot(10L, event, date1, OccurrenceStatus.SCHEDULED);
        OccurrenceSlotDto occ2 = occurrenceSlot(11L, event, date2, OccurrenceStatus.SCHEDULED);

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(occurrenceService.findSlotsByEvent(eq(1L), any()))
                .thenReturn(List.of(occ1, occ2));

        List<AllocationResponseDto> results = service.allocateManuallyFromDate(
                new AllocateFromDateRequestDto(1L, date1, 5, "obs"));

        assertThat(results).hasSize(2);
        verify(allocationRepository, times(2)).save(any());
        verify(occurrenceService).markAssigned(List.of(10L, 11L));
    }

    @Test
    @DisplayName("allocateManuallyFromDate: una ocurrencia futura choca contra otro evento → 409, nada se aplica")
    void allocateManuallyFromDateConSolape() {
        RecurringEvent event = recurringEvent(1L);
        LocalDate date1 = futureDate(1);
        OccurrenceSlotDto occ1 = occurrenceSlot(10L, event, date1, OccurrenceStatus.SCHEDULED);

        RecurringEvent foreignEvent = recurringEvent(99L);
        OccurrenceSlotDto foreignOccurrence = occurrenceSlot(50L, foreignEvent, date1, OccurrenceStatus.ASSIGNED);
        Allocation foreignAllocation = allocation(500L, foreignOccurrence, 5);

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(occurrenceService.findSlotsByEvent(eq(1L), any()))
                .thenReturn(List.of(occ1));
        when(occurrenceService.findSlotsByStatusBetween(any(), any(), any())).thenReturn(List.of(foreignOccurrence));
        when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of(foreignAllocation));

        assertThatThrownBy(() -> service.allocateManuallyFromDate(
                new AllocateFromDateRequestDto(1L, date1, 5, null)))
                .isInstanceOf(ReassignConflictException.class);

        verify(allocationRepository, never()).save(any());
        verify(occurrenceService, never()).markAssigned(any());
    }

    @Test
    @DisplayName("allocateManuallyFromDate: evento UniqueEvent → conflicto, no soportado")
    void allocateManuallyFromDateEventoUnico() {
        UniqueEvent uniqueEvent = UniqueEvent.builder()
                .id(1L)
                .enrolled(20)
                .startTime(LocalTime.of(18, 0))
                .duration(Duration.ofMinutes(120))
                .date(futureDate(2))
                .build();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(uniqueEvent));

        assertThatThrownBy(() -> service.allocateManuallyFromDate(
                new AllocateFromDateRequestDto(1L, futureDate(1), 5, null)))
                .isInstanceOf(AllocationConflictException.class);

        verify(allocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("allocateManuallyFromDate: fromDate pasada se clampea a hoy")
    void allocateManuallyFromDateFromDatePasadaSeClampeaAHoy() {
        RecurringEvent event = recurringEvent(1L);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(occurrenceService.findSlotsByEvent(any(), any())).thenReturn(List.of());

        service.allocateManuallyFromDate(new AllocateFromDateRequestDto(1L, LocalDate.now().minusDays(5), 5, null));

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(occurrenceService).findSlotsByEvent(eq(1L), dateCaptor.capture());
        assertThat(dateCaptor.getValue()).isEqualTo(LocalDate.now());
    }

    // ---------- reassignEvent ----------

    @Test
    @DisplayName("reassignEvent: evento en curso, asigna las ocurrencias futuras devueltas por el repo")
    void reassignEventFeliz() {
        RecurringEvent event = recurringEvent(1L);
        OccurrenceSlotDto occ1 = occurrenceSlot(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED);
        OccurrenceSlotDto occ2 = occurrenceSlot(11L, event, futureDate(8), OccurrenceStatus.SCHEDULED);

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(occurrenceService.findSlotsByEvent(eq(1L), any()))
                .thenReturn(List.of(occ1, occ2));

        List<AllocationResponseDto> results = service.reassignEvent(1L, new AllocateOccurrenceRequestDto(5, "obs"));

        assertThat(results).hasSize(2);
        verify(allocationRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("reassignEvent: evento finalizado (sin ocurrencias futuras) → conflicto, no persiste")
    void reassignEventFinalizado() {
        RecurringEvent event = recurringEvent(1L);
        OccurrenceSlotDto pastOcc = occurrenceSlot(10L, event, LocalDate.now().minusDays(1), OccurrenceStatus.SCHEDULED);

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(occurrenceService.findSlotsByEvent(eq(1L), any()))
                .thenReturn(List.of(pastOcc));

        assertThatThrownBy(() -> service.reassignEvent(1L, new AllocateOccurrenceRequestDto(5, null)))
                .isInstanceOf(AllocationConflictException.class);

        verify(allocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("reassignEvent: evento no recurrente (UniqueEvent) → conflicto, no soportado")
    void reassignEventEventoUnico() {
        UniqueEvent uniqueEvent = UniqueEvent.builder()
                .id(1L)
                .enrolled(20)
                .startTime(LocalTime.of(18, 0))
                .duration(Duration.ofMinutes(120))
                .date(futureDate(2))
                .build();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(uniqueEvent));

        assertThatThrownBy(() -> service.reassignEvent(1L, new AllocateOccurrenceRequestDto(5, null)))
                .isInstanceOf(AllocationConflictException.class);

        verify(allocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("reassignEvent: evento inexistente → ResourceNotFoundException")
    void reassignEventEventoInexistente() {
        when(eventRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reassignEvent(1L, new AllocateOccurrenceRequestDto(5, null)))
                .isInstanceOf(ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException.class);

        verify(allocationRepository, never()).save(any());
    }

    // ---------- importAllocationsFromDate ----------

    @Test
    @DisplayName("importAllocationsFromDate: incluye ocurrencias pasadas (no las saltea)")
    void importAssignmentsFromDateIncluyeOcurrenciasPasadas() {
        RecurringEvent event = recurringEvent(1L);
        OccurrenceSlotDto pastOcc = occurrenceSlot(10L, event, LocalDate.now().minusDays(3), OccurrenceStatus.SCHEDULED);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(occurrenceService.findSlotsByEvent(eq(1L), any()))
                .thenReturn(List.of(pastOcc));

        int count = service.importAllocationsFromDate(
                new AllocateFromDateRequestDto(1L, LocalDate.now().minusDays(3), 5, null));

        assertThat(count).isEqualTo(1);
        verify(allocationRepository).save(any());
        verify(occurrenceService).markAssigned(List.of(10L));
    }

    @Test
    @DisplayName("importAllocationsFromDate: estampa source=IMPORTED en la asignación nueva")
    void importAssignmentsFromDateSourceImported() {
        RecurringEvent event = recurringEvent(1L);
        OccurrenceSlotDto occ = occurrenceSlot(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(occurrenceService.findSlotsByEvent(eq(1L), any()))
                .thenReturn(List.of(occ));

        service.importAllocationsFromDate(new AllocateFromDateRequestDto(1L, futureDate(1), 5, null));

        verify(allocationRepository).save(argThat(a -> a.getSource() == AllocationSource.IMPORTED));
    }

    @Test
    @DisplayName("importAllocationsFromDate: reusa la asignación existente de la ocurrencia (upsert, no duplica)")
    void importAssignmentsFromDateUpsertReusaExistente() {
        RecurringEvent event = recurringEvent(1L);
        OccurrenceSlotDto occ = occurrenceSlot(10L, event, futureDate(1), OccurrenceStatus.ASSIGNED);
        Allocation existing = allocation(900L, occ, 3);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(occurrenceService.findSlotsByEvent(eq(1L), any()))
                .thenReturn(List.of(occ));
        when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of(existing));

        service.importAllocationsFromDate(new AllocateFromDateRequestDto(1L, futureDate(1), 5, null));

        // existing llega managed; el writer ya no llama save() explícito (dirty checking).
        assertThat(existing.getClassroomId()).isEqualTo(5);
        assertThat(existing.getSource()).isEqualTo(AllocationSource.IMPORTED);
    }

    @Test
    @DisplayName("importAllocationsFromDate: saltea ocurrencias CANCELLED/SUSPENDED")
    void importAssignmentsFromDateSalteaNoAsignables() {
        RecurringEvent event = recurringEvent(1L);
        OccurrenceSlotDto cancelled = occurrenceSlot(10L, event, futureDate(1), OccurrenceStatus.CANCELLED);
        OccurrenceSlotDto suspended = occurrenceSlot(11L, event, futureDate(2), OccurrenceStatus.SUSPENDED);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(occurrenceService.findSlotsByEvent(eq(1L), any()))
                .thenReturn(List.of(cancelled, suspended));

        int count = service.importAllocationsFromDate(
                new AllocateFromDateRequestDto(1L, futureDate(1), 5, null));

        assertThat(count).isZero();
        verify(allocationRepository, never()).save(any());
        verify(occurrenceService, never()).markAssigned(any());
    }

    // ---------- importAllocationsBatch ----------

    @Test
    @DisplayName("importAllocationsBatch: aplica occurrences de varios eventos con su propia aula, en un solo pase")
    void importAllocationsBatchVariosEventos() {
        RecurringEvent event1 = recurringEvent(1L);
        RecurringEvent event2 = recurringEvent(2L);
        OccurrenceSlotDto occ1 = occurrenceSlot(10L, event1, futureDate(1), OccurrenceStatus.SCHEDULED);
        OccurrenceSlotDto occ2 = occurrenceSlot(20L, event2, futureDate(1), OccurrenceStatus.SCHEDULED);
        when(occurrenceService.findSlotsByEvents(any(), any()))
                .thenReturn(List.of(occ1, occ2));

        int count = service.importAllocationsBatch(List.of(
                new AllocateFromDateRequestDto(1L, futureDate(1), 5, "Importado de Excel"),
                new AllocateFromDateRequestDto(2L, futureDate(1), 7, "Importado de Excel")));

        assertThat(count).isEqualTo(2);
        verify(allocationRepository).save(argThat(a -> a.getOccurrenceId().equals(10L) && a.getClassroomId() == 5));
        verify(allocationRepository).save(argThat(a -> a.getOccurrenceId().equals(20L) && a.getClassroomId() == 7));
    }

    @Test
    @DisplayName("importAllocationsBatch: filtra en memoria las occurrences anteriores a la fecha propia de cada evento")
    void importAllocationsBatchFiltraPorFromDatePropio() {
        RecurringEvent event1 = recurringEvent(1L);
        RecurringEvent event2 = recurringEvent(2L);
        // occ1 es anterior al fromDate de event1 (2do cuatrimestre vs 1ro): la query trae de más
        // (usa la fecha más antigua de todo el batch), el filtro en memoria la debe descartar.
        OccurrenceSlotDto occ1 = occurrenceSlot(10L, event1, futureDate(1), OccurrenceStatus.SCHEDULED);
        OccurrenceSlotDto occ2 = occurrenceSlot(20L, event2, futureDate(5), OccurrenceStatus.SCHEDULED);
        when(occurrenceService.findSlotsByEvents(any(), any()))
                .thenReturn(List.of(occ1, occ2));

        int count = service.importAllocationsBatch(List.of(
                new AllocateFromDateRequestDto(1L, futureDate(3), 5, "Importado de Excel"),
                new AllocateFromDateRequestDto(2L, futureDate(1), 7, "Importado de Excel")));

        assertThat(count).isEqualTo(1);
        verify(allocationRepository).save(argThat(a -> a.getOccurrenceId().equals(20L)));
    }

    @Test
    @DisplayName("importAllocationsBatch: lista vacía no consulta nada y devuelve 0")
    void importAllocationsBatchListaVacia() {
        int count = service.importAllocationsBatch(List.of());

        assertThat(count).isZero();
        verify(occurrenceService, never()).findSlotsByEvents(any(), any());
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

    private OccurrenceSlotDto occurrenceSlot(long id, AcademicEvent event, LocalDate date, OccurrenceStatus status) {
        return new OccurrenceSlotDto(id, event.getId(), date, event.getStartTime(), event.endTime(), status, event.getEnrolled());
    }

    private Allocation allocation(long id, OccurrenceSlotDto occurrence, Integer classroomId) {
        return Allocation.builder()
                .id(id)
                .occurrenceId(occurrence.occurrenceId())
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

    private AllocationResponseDto dummyResponseDto() {
        return new AllocationResponseDto(1L, AllocationSource.MANUAL, LocalDateTime.now(), null, null, null, null);
    }

    private LocalDate futureDate(int daysFromNow) {
        return LocalDate.now().plusDays(daysFromNow);
    }
}
