package ar.edu.utn.frc.siga.allocation.validator;

import ar.edu.utn.frc.siga.allocation.AllocationTestData;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceConflictDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.exception.ReassignConflictException;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator.AllocationCandidate;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator.OccupiedSlot;
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

    private AllocationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AllocationValidator(classroomService, allocationRepository);
    }

    // ---------- databaseConflicts / validateNoOverlap ----------

    @Test
    @DisplayName("databaseConflicts: misma aula, misma fecha, franjas que se pisan → conflicto")
    void databaseConflictsSolapa() {
        RecurringEvent event = AllocationTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        Occurrence occurrence = AllocationTestData.occurrence(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED);
        AllocationCandidate candidate = new AllocationCandidate(occurrence, 5);
        OccupiedSlot occupied = new OccupiedSlot(5, futureDate(1), LocalTime.of(8, 30), LocalTime.of(9, 0), 99L, 500L);

        List<OccurrenceConflictDto> conflicts = validator.databaseConflicts(List.of(candidate), List.of(occupied));

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.getFirst().conflictingEventId()).isEqualTo(99L);
        assertThat(conflicts.getFirst().conflictingAllocationId()).isEqualTo(500L);
    }

    @Test
    @DisplayName("databaseConflicts: distinta aula o distinta fecha → sin conflicto")
    void databaseConflictsSinSolape() {
        RecurringEvent event = AllocationTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        Occurrence occurrence = AllocationTestData.occurrence(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED);
        AllocationCandidate candidate = new AllocationCandidate(occurrence, 5);
        OccupiedSlot distintaAula = new OccupiedSlot(6, futureDate(1), LocalTime.of(8, 30), LocalTime.of(9, 0), 99L, 500L);
        OccupiedSlot distintaFecha = new OccupiedSlot(5, futureDate(2), LocalTime.of(8, 30), LocalTime.of(9, 0), 99L, 500L);

        assertThat(validator.databaseConflicts(List.of(candidate), List.of(distintaAula, distintaFecha))).isEmpty();
    }

    @Test
    @DisplayName("Borde: fin del nuevo == inicio del ocupante no es solapamiento")
    void bordeFinIgualInicioNoSolapa() {
        RecurringEvent event = AllocationTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90)); // 08:00-09:30
        Occurrence occurrence = AllocationTestData.occurrence(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED);
        AllocationCandidate candidate = new AllocationCandidate(occurrence, 5);
        OccupiedSlot adyacente = new OccupiedSlot(5, futureDate(1), LocalTime.of(9, 30), LocalTime.of(10, 30), 99L, 500L);

        assertThat(validator.databaseConflicts(List.of(candidate), List.of(adyacente))).isEmpty();
    }

    @Test
    @DisplayName("internalConflicts: misma aula/fecha/franja entre dos candidatos de eventos distintos → conflicto")
    void internalConflictsSolapaEntreEventosDistintos() {
        RecurringEvent event1 = AllocationTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        RecurringEvent event2 = AllocationTestData.recurringEvent(2L, LocalTime.of(8, 30), Duration.ofMinutes(60));
        LocalDate date = futureDate(1);
        AllocationCandidate a = new AllocationCandidate(AllocationTestData.occurrence(10L, event1, date, OccurrenceStatus.SCHEDULED), 5);
        AllocationCandidate b = new AllocationCandidate(AllocationTestData.occurrence(11L, event2, date, OccurrenceStatus.SCHEDULED), 5);

        List<OccurrenceConflictDto> conflicts = validator.internalConflicts(List.of(a, b));

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.getFirst().conflictingAllocationId()).isNull();
        assertThat(conflicts.getFirst().conflictingEventId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("internalConflicts: dos ocurrencias del MISMO evento nunca conflictúan entre sí")
    void internalConflictsSalteaMismoEvento() {
        RecurringEvent event = AllocationTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        LocalDate date = futureDate(1);
        // Mismo evento, misma fecha/aula (caso artificial para forzar el chequeo del skip).
        AllocationCandidate a = new AllocationCandidate(AllocationTestData.occurrence(10L, event, date, OccurrenceStatus.SCHEDULED), 5);
        AllocationCandidate b = new AllocationCandidate(AllocationTestData.occurrence(11L, event, date, OccurrenceStatus.SCHEDULED), 5);

        assertThat(validator.internalConflicts(List.of(a, b))).isEmpty();
    }

    @Test
    @DisplayName("validateNoOverlap: sin conflictos no lanza")
    void validateNoOverlapSinConflictosNoLanza() {
        RecurringEvent event = AllocationTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        Occurrence occurrence = AllocationTestData.occurrence(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED);
        AllocationCandidate candidate = new AllocationCandidate(occurrence, 5);

        assertThatCode(() -> validator.validateNoOverlap(List.of(candidate), List.of())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateNoOverlap: con conflictos lanza ReassignConflictException con el detalle")
    void validateNoOverlapConConflictosLanza() {
        RecurringEvent event = AllocationTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        Occurrence occurrence = AllocationTestData.occurrence(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED);
        AllocationCandidate candidate = new AllocationCandidate(occurrence, 5);
        OccupiedSlot occupied = new OccupiedSlot(5, futureDate(1), LocalTime.of(8, 30), LocalTime.of(9, 0), 99L, 500L);

        assertThatThrownBy(() -> validator.validateNoOverlap(List.of(candidate), List.of(occupied)))
                .isInstanceOf(ReassignConflictException.class)
                .satisfies(ex -> assertThat(((ReassignConflictException) ex).getConflicts()).hasSize(1));
    }

    @Test
    @DisplayName("validateNoOverlap(candidates): carga la ocupación firme de BD y detecta el choque")
    void validateNoOverlapCargaBdYDetecta() {
        LocalDate date = futureDate(1);
        RecurringEvent event = AllocationTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        Occurrence occurrence = AllocationTestData.occurrence(10L, event, date, OccurrenceStatus.SCHEDULED);
        AllocationCandidate candidate = new AllocationCandidate(occurrence, 5);

        RecurringEvent occupantEvent = AllocationTestData.recurringEvent(2L, LocalTime.of(8, 30), Duration.ofMinutes(60));
        Occurrence occupantOcc = AllocationTestData.occurrence(20L, occupantEvent, date, OccurrenceStatus.ASSIGNED);
        Allocation occupied = Allocation.builder().id(500L).occurrence(occupantOcc).classroomId(5).build();
        when(allocationRepository.findOccupancyBetween(any(), any(), any())).thenReturn(List.of(occupied));

        assertThatThrownBy(() -> validator.validateNoOverlap(List.of(candidate)))
                .isInstanceOf(ReassignConflictException.class)
                .satisfies(ex -> assertThat(((ReassignConflictException) ex).getConflicts()).hasSize(1));
    }

    @Test
    @DisplayName("validateNoOverlap(candidates): solo ocurrencias pasadas → no consulta BD ni lanza")
    void validateNoOverlapSoloPasadasNoConsultaBd() {
        RecurringEvent event = AllocationTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        Occurrence pasada = AllocationTestData.occurrence(10L, event, LocalDate.now().minusDays(1), OccurrenceStatus.SCHEDULED);

        assertThatCode(() -> validator.validateNoOverlap(List.of(new AllocationCandidate(pasada, 5))))
                .doesNotThrowAnyException();
        org.mockito.Mockito.verifyNoInteractions(allocationRepository);
    }

    // ---------- estado de la ocurrencia ----------

    @Test
    @DisplayName("validateNotPast: ocurrencia pasada lanza AllocationConflictException")
    void validateNotPastOcurrenciaPasadaLanza() {
        RecurringEvent event = AllocationTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        Occurrence occurrence = AllocationTestData.occurrence(10L, event, LocalDate.now().minusDays(1), OccurrenceStatus.SCHEDULED);

        assertThatThrownBy(() -> validator.validateNotPast(occurrence))
                .isInstanceOf(AllocationConflictException.class);
    }

    @Test
    @DisplayName("validateNotPast: ocurrencia futura no lanza")
    void validateNotPastOcurrenciaFuturaNoLanza() {
        RecurringEvent event = AllocationTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        Occurrence occurrence = AllocationTestData.occurrence(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED);

        assertThatCode(() -> validator.validateNotPast(occurrence)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateAssignable: CANCELLED y SUSPENDED lanzan AllocationConflictException")
    void validateAssignableNoAsignableLanza() {
        RecurringEvent event = AllocationTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        Occurrence cancelled = AllocationTestData.occurrence(10L, event, futureDate(1), OccurrenceStatus.CANCELLED);
        Occurrence suspended = AllocationTestData.occurrence(11L, event, futureDate(1), OccurrenceStatus.SUSPENDED);

        assertThatThrownBy(() -> validator.validateAssignable(cancelled)).isInstanceOf(AllocationConflictException.class);
        assertThatThrownBy(() -> validator.validateAssignable(suspended)).isInstanceOf(AllocationConflictException.class);
    }

    @Test
    @DisplayName("validateAssignable: SCHEDULED/ASSIGNED no lanzan")
    void validateAssignableAsignableNoLanza() {
        RecurringEvent event = AllocationTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        Occurrence scheduled = AllocationTestData.occurrence(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED);

        assertThatCode(() -> validator.validateAssignable(scheduled)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("isApplicable: combina no-pasada + asignable")
    void isApplicableCombinado() {
        RecurringEvent event = AllocationTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        Occurrence futuraAsignable = AllocationTestData.occurrence(10L, event, futureDate(1), OccurrenceStatus.SCHEDULED);
        Occurrence futuraCancelada = AllocationTestData.occurrence(11L, event, futureDate(1), OccurrenceStatus.CANCELLED);
        Occurrence pasadaAsignable = AllocationTestData.occurrence(12L, event, LocalDate.now().minusDays(1), OccurrenceStatus.SCHEDULED);

        assertThat(validator.isApplicable(futuraAsignable)).isTrue();
        assertThat(validator.isApplicable(futuraCancelada)).isFalse();
        assertThat(validator.isApplicable(pasadaAsignable)).isFalse();
    }

    @Test
    @DisplayName("validateEventNotFinished: todas las ocurrencias pasadas lanza AllocationConflictException")
    void validateEventNotFinishedTodasPasadasLanza() {
        RecurringEvent event = AllocationTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        Occurrence pasada = AllocationTestData.occurrence(10L, event, LocalDate.now().minusDays(1), OccurrenceStatus.SCHEDULED);

        assertThatThrownBy(() -> validator.validateEventNotFinished(List.of(pasada)))
                .isInstanceOf(AllocationConflictException.class);
    }

    @Test
    @DisplayName("validateEventNotFinished: al menos una ocurrencia futura no lanza")
    void validateEventNotFinishedConFuturaNoLanza() {
        RecurringEvent event = AllocationTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        Occurrence pasada = AllocationTestData.occurrence(10L, event, LocalDate.now().minusDays(1), OccurrenceStatus.SCHEDULED);
        Occurrence futura = AllocationTestData.occurrence(11L, event, futureDate(1), OccurrenceStatus.SCHEDULED);

        assertThatCode(() -> validator.validateEventNotFinished(List.of(pasada, futura))).doesNotThrowAnyException();
    }

    // ---------- aulas ----------

    @Test
    @DisplayName("validateClassroomsAvailable: aula inexistente lanza AllocationConflictException")
    void validateClassroomsAvailableInexistenteLanza() {
        when(classroomService.findByIds(any())).thenReturn(List.of());

        assertThatThrownBy(() -> validator.validateClassroomsAvailable(Set.of(5)))
                .isInstanceOf(AllocationConflictException.class)
                .hasMessageContaining("5");
    }

    @Test
    @DisplayName("validateClassroomsAvailable: aula available=false lanza AllocationConflictException")
    void validateClassroomsAvailableNoDisponibleLanza() {
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, false)));

        assertThatThrownBy(() -> validator.validateClassroomsAvailable(Set.of(5)))
                .isInstanceOf(AllocationConflictException.class)
                .hasMessageContaining("5");
    }

    @Test
    @DisplayName("validateClassroomsAvailable: aula available=true no lanza")
    void validateClassroomsAvailableDisponibleNoLanza() {
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, true)));

        assertThatCode(() -> validator.validateClassroomsAvailable(Set.of(5))).doesNotThrowAnyException();
    }

    // ---------- helpers ----------

    private ClassroomResponseDto classroom(Integer id, boolean available) {
        return new ClassroomResponseDto(id, "Aula " + id, 1, 100, available, 1, "Edificio 1", 1, "Tipo");
    }

    private LocalDate futureDate(int daysFromNow) {
        return LocalDate.now().plusDays(daysFromNow);
    }
}
