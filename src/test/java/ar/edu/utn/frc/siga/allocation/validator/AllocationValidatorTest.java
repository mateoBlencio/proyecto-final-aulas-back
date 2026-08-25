package ar.edu.utn.frc.siga.allocation.validator;

import ar.edu.utn.frc.siga.events.EventTestData;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceConflictDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.exception.ReallocationConflictException;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AllocationValidator")
class AllocationValidatorTest {

    @Mock
    private ClassroomService classroomService;
    @Mock
    private AllocationRepository allocationRepository;
    @Mock
    private OccurrenceService occurrenceService;

    private AllocationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AllocationValidator(classroomService, allocationRepository, occurrenceService);
    }

    // ---------- databaseConflicts / validateNoOverlap ----------

    @Test
    @DisplayName("databaseConflicts: misma aula, misma fecha, franjas que se pisan → conflicto")
    void databaseConflictsSolapa() {
        RecurringEvent event = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        OccurrenceSlotDto occurrence = EventTestData.occurrenceSlot(10L, event, futureDate(1), OccurrenceStatus.NEEDS_ROOM);
        AllocationCandidate candidate = new AllocationCandidate(occurrence, 5L);
        OccupiedSlot occupied = new OccupiedSlot(5L, futureDate(1), LocalTime.of(8, 30), LocalTime.of(9, 0), 99L, 500L);

        List<OccurrenceConflictDto> conflicts = validator.databaseConflicts(List.of(candidate), List.of(occupied));

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.getFirst().conflictingEventId()).isEqualTo(99L);
        assertThat(conflicts.getFirst().conflictingAllocationId()).isEqualTo(500L);
    }

    @Test
    @DisplayName("databaseConflicts: distinta aula o distinta fecha → sin conflicto")
    void databaseConflictsSinSolape() {
        RecurringEvent event = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        OccurrenceSlotDto occurrence = EventTestData.occurrenceSlot(10L, event, futureDate(1), OccurrenceStatus.NEEDS_ROOM);
        AllocationCandidate candidate = new AllocationCandidate(occurrence, 5L);
        OccupiedSlot distintaAula = new OccupiedSlot(6L, futureDate(1), LocalTime.of(8, 30), LocalTime.of(9, 0), 99L, 500L);
        OccupiedSlot distintaFecha = new OccupiedSlot(5L, futureDate(2), LocalTime.of(8, 30), LocalTime.of(9, 0), 99L, 500L);

        assertThat(validator.databaseConflicts(List.of(candidate), List.of(distintaAula, distintaFecha))).isEmpty();
    }

    @Test
    @DisplayName("Borde: fin del nuevo == inicio del ocupante no es solapamiento")
    void bordeFinIgualInicioNoSolapa() {
        RecurringEvent event = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90)); // 08:00-09:30
        OccurrenceSlotDto occurrence = EventTestData.occurrenceSlot(10L, event, futureDate(1), OccurrenceStatus.NEEDS_ROOM);
        AllocationCandidate candidate = new AllocationCandidate(occurrence, 5L);
        OccupiedSlot adyacente = new OccupiedSlot(5L, futureDate(1), LocalTime.of(9, 30), LocalTime.of(10, 30), 99L, 500L);

        assertThat(validator.databaseConflicts(List.of(candidate), List.of(adyacente))).isEmpty();
    }

    @Test
    @DisplayName("internalConflicts: misma aula/fecha/franja entre dos candidatos de eventos distintos → conflicto")
    void internalConflictsSolapaEntreEventosDistintos() {
        RecurringEvent event1 = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        RecurringEvent event2 = EventTestData.recurringEvent(2L, LocalTime.of(8, 30), Duration.ofMinutes(60));
        LocalDate date = futureDate(1);
        AllocationCandidate a = new AllocationCandidate(EventTestData.occurrenceSlot(10L, event1, date, OccurrenceStatus.NEEDS_ROOM), 5L);
        AllocationCandidate b = new AllocationCandidate(EventTestData.occurrenceSlot(11L, event2, date, OccurrenceStatus.NEEDS_ROOM), 5L);

        List<OccurrenceConflictDto> conflicts = validator.internalConflicts(List.of(a, b));

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.getFirst().conflictingAllocationId()).isNull();
        assertThat(conflicts.getFirst().conflictingEventId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("internalConflicts: dos ocurrencias del MISMO evento nunca conflictúan entre sí")
    void internalConflictsSalteaMismoEvento() {
        RecurringEvent event = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        LocalDate date = futureDate(1);
        // Mismo evento, misma fecha/aula (caso artificial para forzar el chequeo del skip).
        AllocationCandidate a = new AllocationCandidate(EventTestData.occurrenceSlot(10L, event, date, OccurrenceStatus.NEEDS_ROOM), 5L);
        AllocationCandidate b = new AllocationCandidate(EventTestData.occurrenceSlot(11L, event, date, OccurrenceStatus.NEEDS_ROOM), 5L);

        assertThat(validator.internalConflicts(List.of(a, b))).isEmpty();
    }

    @Test
    @DisplayName("validateNoOverlap: sin conflictos no lanza")
    void validateNoOverlapSinConflictosNoLanza() {
        RecurringEvent event = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        OccurrenceSlotDto occurrence = EventTestData.occurrenceSlot(10L, event, futureDate(1), OccurrenceStatus.NEEDS_ROOM);
        AllocationCandidate candidate = new AllocationCandidate(occurrence, 5L);

        assertThatCode(() -> validator.validateNoOverlap(List.of(candidate), List.of())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateNoOverlap: con conflictos lanza ReallocationConflictException con el detalle")
    void validateNoOverlapConConflictosLanza() {
        RecurringEvent event = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        OccurrenceSlotDto occurrence = EventTestData.occurrenceSlot(10L, event, futureDate(1), OccurrenceStatus.NEEDS_ROOM);
        AllocationCandidate candidate = new AllocationCandidate(occurrence, 5L);
        OccupiedSlot occupied = new OccupiedSlot(5L, futureDate(1), LocalTime.of(8, 30), LocalTime.of(9, 0), 99L, 500L);

        assertThatThrownBy(() -> validator.validateNoOverlap(List.of(candidate), List.of(occupied)))
                .isInstanceOf(ReallocationConflictException.class)
                .satisfies(ex -> assertThat(((ReallocationConflictException) ex).getConflicts()).hasSize(1));
    }

    @Test
    @DisplayName("validateNoOverlap(candidates): carga la ocupación firme de BD y detecta el choque")
    void validateNoOverlapCargaBdYDetecta() {
        LocalDate date = futureDate(1);
        RecurringEvent event = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        OccurrenceSlotDto occurrence = EventTestData.occurrenceSlot(10L, event, date, OccurrenceStatus.NEEDS_ROOM);
        AllocationCandidate candidate = new AllocationCandidate(occurrence, 5L);

        RecurringEvent occupantEvent = EventTestData.recurringEvent(2L, LocalTime.of(8, 30), Duration.ofMinutes(60));
        OccurrenceSlotDto occupantOcc = EventTestData.occurrenceSlot(20L, occupantEvent, date, OccurrenceStatus.NEEDS_ROOM);
        Allocation occupied = Allocation.builder().id(500L).occurrenceId(20L).classroomId(5L).build();
        when(occurrenceService.findSlotsBetween(any(), any())).thenReturn(List.of(occupantOcc));
        when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of(occupied));

        assertThatThrownBy(() -> validator.validateNoOverlap(List.of(candidate)))
                .isInstanceOf(ReallocationConflictException.class)
                .satisfies(ex -> assertThat(((ReallocationConflictException) ex).getConflicts()).hasSize(1));
    }

    @Test
    @DisplayName("validateNoOverlap(candidates): solo ocurrencias pasadas → no consulta BD ni lanza")
    void validateNoOverlapSoloPasadasNoConsultaBd() {
        RecurringEvent event = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        OccurrenceSlotDto pasada = EventTestData.occurrenceSlot(10L, event, LocalDate.now().minusDays(1), OccurrenceStatus.NEEDS_ROOM);

        assertThatCode(() -> validator.validateNoOverlap(List.of(new AllocationCandidate(pasada, 5L))))
                .doesNotThrowAnyException();
        org.mockito.Mockito.verifyNoInteractions(allocationRepository);
    }

    // ---------- estado de la ocurrencia ----------

    @Test
    @DisplayName("validateNotPast: ocurrencia pasada lanza AllocationConflictException")
    void validateNotPastOcurrenciaPasadaLanza() {
        RecurringEvent event = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        OccurrenceSlotDto occurrence = EventTestData.occurrenceSlot(10L, event, LocalDate.now().minusDays(1), OccurrenceStatus.NEEDS_ROOM);

        assertThatThrownBy(() -> validator.validateNotPast(occurrence))
                .isInstanceOf(AllocationConflictException.class);
    }

    @Test
    @DisplayName("validateNotPast: ocurrencia futura no lanza")
    void validateNotPastOcurrenciaFuturaNoLanza() {
        RecurringEvent event = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        OccurrenceSlotDto occurrence = EventTestData.occurrenceSlot(10L, event, futureDate(1), OccurrenceStatus.NEEDS_ROOM);

        assertThatCode(() -> validator.validateNotPast(occurrence)).doesNotThrowAnyException();
    }

    // ---------- aulas ----------

    @Test
    @DisplayName("validateClassroomsAvailable: aula inexistente lanza AllocationConflictException")
    void validateClassroomsAvailableInexistenteLanza() {
        when(classroomService.findByIds(any())).thenReturn(List.of());

        assertThatThrownBy(() -> validator.validateClassroomsAvailable(Set.of(5L)))
                .isInstanceOf(AllocationConflictException.class)
                .hasMessageContaining("5");
    }

    @Test
    @DisplayName("validateClassroomsAvailable: aula existente no lanza")
    void validateClassroomsAvailableDisponibleNoLanza() {
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5L)));

        assertThatCode(() -> validator.validateClassroomsAvailable(Set.of(5L))).doesNotThrowAnyException();
    }

    // ---------- helpers ----------

    private ClassroomResponseDto classroom(Long id) {
        return new ClassroomResponseDto(id, id.intValue(), 100, 1L, "Edificio 1", 1L, "Tipo");
    }

    private LocalDate futureDate(int daysFromNow) {
        return LocalDate.now().plusDays(daysFromNow);
    }
}
