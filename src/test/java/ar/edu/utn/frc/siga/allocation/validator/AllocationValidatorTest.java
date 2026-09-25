package ar.edu.utn.frc.siga.allocation.validator;

import ar.edu.utn.frc.siga.events.EventTestData;
import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceConflictDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.exception.OverlapObservationRequiredException;
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

    // Margen mutable por caso: setUp lo lee vía lambda en vez de fijarlo en una constante,
    // para que cada test elija el margen sin mockear el módulo settings.
    private int maxOverlapMinutes = 40;

    private AllocationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AllocationValidator(classroomService, allocationRepository, occurrenceService,
                () -> maxOverlapMinutes);
    }

    // ---------- databaseConflicts / validateNoOverlap ----------

    @Test
    @DisplayName("databaseConflicts: misma aula, misma fecha, franjas que se pisan → conflicto")
    void databaseConflictsSolapa() {
        RecurringEvent event = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        OccurrenceSlotDto occurrence = EventTestData.occurrenceSlot(10L, event, futureDate(1), OccurrenceStatus.NEEDS_ROOM);
        AllocationCandidate candidate = new AllocationCandidate(occurrence, 5L);
        // Estirado a 60 minutos de solape (antes 30) para que siga bloqueando bajo el margen default de 40.
        OccupiedSlot occupied = new OccupiedSlot(5L, futureDate(1), LocalTime.of(8, 30), LocalTime.of(10, 0), 99L, 500L);

        List<OccurrenceConflictDto> conflicts = validator.databaseConflicts(List.of(candidate), List.of(occupied));

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.getFirst().conflictingEventId()).isEqualTo(99L);
        assertThat(conflicts.getFirst().conflictingAllocationId()).isEqualTo(500L);
        assertThat(conflicts.getFirst().overlapMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("reviewManualOverlaps: solape de 30 minutos con margen 40 → tolerated, no blocking")
    void reviewManualOverlapsSolape30ConMargen40QuedaTolerado() {
        LocalDate date = futureDate(1);
        RecurringEvent event = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90)); // 08:00-09:30
        OccurrenceSlotDto occurrence = EventTestData.occurrenceSlot(10L, event, date, OccurrenceStatus.NEEDS_ROOM);
        AllocationCandidate candidate = new AllocationCandidate(occurrence, 5L);

        RecurringEvent occupantEvent = EventTestData.recurringEvent(2L, LocalTime.of(9, 0), Duration.ofMinutes(60)); // 09:00-10:00, solape 30
        OccurrenceSlotDto occupantOcc = EventTestData.occurrenceSlot(20L, occupantEvent, date, OccurrenceStatus.NEEDS_ROOM);
        Allocation occupied = Allocation.builder().id(500L).occurrenceId(20L).classroomId(5L).build();
        when(occurrenceService.findSlotsBetween(any(), any())).thenReturn(List.of(occupantOcc));
        when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of(occupied));

        OverlapReview review = validator.reviewManualOverlaps(List.of(candidate));

        assertThat(review.blocking()).isEmpty();
        assertThat(review.tolerated()).hasSize(1);
        assertThat(review.tolerated().getFirst().overlapMinutes()).isEqualTo(30);
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
        assertThat(conflicts.getFirst().overlapMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("reviewManualOverlaps: solape interno de 30 entre eventos distintos → tolerated con margen 40")
    void internalConflictsSolape30ConMargen40QuedaTolerado() {
        RecurringEvent event1 = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90)); // 08:00-09:30
        RecurringEvent event2 = EventTestData.recurringEvent(2L, LocalTime.of(9, 0), Duration.ofMinutes(60)); // 09:00-10:00, solape 30
        LocalDate date = futureDate(1);
        AllocationCandidate a = new AllocationCandidate(EventTestData.occurrenceSlot(10L, event1, date, OccurrenceStatus.NEEDS_ROOM), 5L);
        AllocationCandidate b = new AllocationCandidate(EventTestData.occurrenceSlot(11L, event2, date, OccurrenceStatus.NEEDS_ROOM), 5L);

        OverlapReview review = validator.reviewManualOverlaps(List.of(a, b));

        assertThat(review.blocking()).isEmpty();
        assertThat(review.tolerated()).hasSize(1);
        assertThat(review.tolerated().getFirst().overlapMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("internalConflicts: dos ocurrencias del MISMO evento nunca conflictúan entre sí")
    void internalConflictsSalteaMismoEvento() {
        // Mismo evento completo (08:00-09:30, 90 minutos de solape) para que el test solo pueda
        // pasar por el skip de "mismo evento": con un solape chico, el test pasaría igual aunque
        // alguien borre esa condición.
        RecurringEvent event = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        LocalDate date = futureDate(1);
        AllocationCandidate a = new AllocationCandidate(EventTestData.occurrenceSlot(10L, event, date, OccurrenceStatus.NEEDS_ROOM), 5L);
        AllocationCandidate b = new AllocationCandidate(EventTestData.occurrenceSlot(11L, event, date, OccurrenceStatus.NEEDS_ROOM), 5L);

        assertThat(validator.internalConflicts(List.of(a, b))).isEmpty();
    }

    @Test
    @DisplayName("regresión: trío de ocurrencias simultáneas del mismo evento (misma fecha, 3 aulas distintas) "
            + "no se marca como conflicto entre sí")
    void internalConflictsNoMarcaTrioSimultaneo() {
        RecurringEvent event = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        LocalDate date = futureDate(1);
        AllocationCandidate roomSlot1 = new AllocationCandidate(EventTestData.occurrenceSlot(10L, event, date, OccurrenceStatus.NEEDS_ROOM), 5L);
        AllocationCandidate roomSlot2 = new AllocationCandidate(EventTestData.occurrenceSlot(11L, event, date, OccurrenceStatus.NEEDS_ROOM), 6L);
        AllocationCandidate roomSlot3 = new AllocationCandidate(EventTestData.occurrenceSlot(12L, event, date, OccurrenceStatus.NEEDS_ROOM), 7L);

        assertThat(validator.internalConflicts(List.of(roomSlot1, roomSlot2, roomSlot3))).isEmpty();
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
    @DisplayName("validateNoOverlap: sobrecarga estricta (margen 0) lanza incluso con un solape de 30, que preview no tolera")
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
    @DisplayName("validateManualOverlap(candidates, obs): carga la ocupación firme de BD y detecta el choque")
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

        assertThatThrownBy(() -> validator.validateManualOverlap(List.of(candidate), "obs"))
                .isInstanceOf(ReallocationConflictException.class)
                .satisfies(ex -> assertThat(((ReallocationConflictException) ex).getConflicts()).hasSize(1));
    }

    @Test
    @DisplayName("validateManualOverlap(candidates, obs): solo ocurrencias pasadas → no consulta BD ni lanza")
    void validateNoOverlapSoloPasadasNoConsultaBd() {
        RecurringEvent event = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        OccurrenceSlotDto pasada = EventTestData.occurrenceSlot(10L, event, LocalDate.now().minusDays(1), OccurrenceStatus.NEEDS_ROOM);

        assertThatCode(() -> validator.validateManualOverlap(List.of(new AllocationCandidate(pasada, 5L)), "obs"))
                .doesNotThrowAnyException();
        org.mockito.Mockito.verifyNoInteractions(allocationRepository);
    }

    // ---------- validateManualOverlap: margen y observación obligatoria ----------

    @Test
    @DisplayName("validateManualOverlap: el margen sale del setting inyectado, no de una constante fija")
    void margenSaleDelSettingNoDeUnaConstante() {
        LocalDate date = futureDate(1);
        RecurringEvent event1 = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90)); // 08:00-09:30
        RecurringEvent event2 = EventTestData.recurringEvent(2L, LocalTime.of(9, 0), Duration.ofMinutes(60)); // 09:00-10:00, solape 30
        AllocationCandidate a = new AllocationCandidate(EventTestData.occurrenceSlot(10L, event1, date, OccurrenceStatus.NEEDS_ROOM), 5L);
        AllocationCandidate b = new AllocationCandidate(EventTestData.occurrenceSlot(11L, event2, date, OccurrenceStatus.NEEDS_ROOM), 5L);

        maxOverlapMinutes = 0;
        assertThatThrownBy(() -> validator.validateManualOverlap(List.of(a, b), "obs"))
                .isInstanceOf(ReallocationConflictException.class);

        maxOverlapMinutes = 40;
        assertThatCode(() -> validator.validateManualOverlap(List.of(a, b), "obs"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateManualOverlap: solape tolerado con observación null exige comentario")
    void validateManualOverlapSolapeToleradoSinObservacionNullExigeComentario() {
        LocalDate date = futureDate(1);
        RecurringEvent event1 = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        RecurringEvent event2 = EventTestData.recurringEvent(2L, LocalTime.of(9, 0), Duration.ofMinutes(60));
        AllocationCandidate a = new AllocationCandidate(EventTestData.occurrenceSlot(10L, event1, date, OccurrenceStatus.NEEDS_ROOM), 5L);
        AllocationCandidate b = new AllocationCandidate(EventTestData.occurrenceSlot(11L, event2, date, OccurrenceStatus.NEEDS_ROOM), 5L);

        assertThatThrownBy(() -> validator.validateManualOverlap(List.of(a, b), null))
                .isInstanceOf(OverlapObservationRequiredException.class)
                .satisfies(ex -> {
                    List<OccurrenceConflictDto> overlaps = ((OverlapObservationRequiredException) ex).getOverlaps();
                    assertThat(overlaps).hasSize(1);
                    assertThat(overlaps.getFirst().overlapMinutes()).isEqualTo(30);
                });
    }

    @Test
    @DisplayName("validateManualOverlap: solape tolerado con observación en blanco exige comentario")
    void validateManualOverlapSolapeToleradoConObservacionEnBlancoExigeComentario() {
        LocalDate date = futureDate(1);
        RecurringEvent event1 = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        RecurringEvent event2 = EventTestData.recurringEvent(2L, LocalTime.of(9, 0), Duration.ofMinutes(60));
        AllocationCandidate a = new AllocationCandidate(EventTestData.occurrenceSlot(10L, event1, date, OccurrenceStatus.NEEDS_ROOM), 5L);
        AllocationCandidate b = new AllocationCandidate(EventTestData.occurrenceSlot(11L, event2, date, OccurrenceStatus.NEEDS_ROOM), 5L);

        assertThatThrownBy(() -> validator.validateManualOverlap(List.of(a, b), "   "))
                .isInstanceOf(OverlapObservationRequiredException.class);
    }

    @Test
    @DisplayName("validateManualOverlap: solape tolerado con observación presente no lanza")
    void validateManualOverlapSolapeToleradoConObservacionNoLanza() {
        LocalDate date = futureDate(1);
        RecurringEvent event1 = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90));
        RecurringEvent event2 = EventTestData.recurringEvent(2L, LocalTime.of(9, 0), Duration.ofMinutes(60));
        AllocationCandidate a = new AllocationCandidate(EventTestData.occurrenceSlot(10L, event1, date, OccurrenceStatus.NEEDS_ROOM), 5L);
        AllocationCandidate b = new AllocationCandidate(EventTestData.occurrenceSlot(11L, event2, date, OccurrenceStatus.NEEDS_ROOM), 5L);

        assertThatCode(() -> validator.validateManualOverlap(List.of(a, b), "mudanza de aula por obra"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateManualOverlap: solape que supera el margen lanza aunque haya observación")
    void validateManualOverlapSolapeQueSuperaElMargenLanzaAunqueHayaObservacion() {
        LocalDate date = futureDate(1);
        RecurringEvent event1 = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90)); // 08:00-09:30
        RecurringEvent event2 = EventTestData.recurringEvent(2L, LocalTime.of(8, 0), Duration.ofMinutes(90)); // choque total, solape 90
        AllocationCandidate a = new AllocationCandidate(EventTestData.occurrenceSlot(10L, event1, date, OccurrenceStatus.NEEDS_ROOM), 5L);
        AllocationCandidate b = new AllocationCandidate(EventTestData.occurrenceSlot(11L, event2, date, OccurrenceStatus.NEEDS_ROOM), 5L);

        assertThatThrownBy(() -> validator.validateManualOverlap(List.of(a, b), "tengo un motivo válido"))
                .isInstanceOf(ReallocationConflictException.class);
    }

    @Test
    @DisplayName("validateManualOverlap: un par bloqueante y otro tolerado en el mismo lote → gana el conflicto, no la falta de observación")
    void loteConParBloqueanteYParToleradoLanzaConflictoNoObservacion() {
        LocalDate date = futureDate(1);
        RecurringEvent event1 = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90)); // 08:00-09:30, aula 5
        RecurringEvent event2 = EventTestData.recurringEvent(2L, LocalTime.of(9, 0), Duration.ofMinutes(60)); // 09:00-10:00, solape 30 con event1
        RecurringEvent event3 = EventTestData.recurringEvent(3L, LocalTime.of(8, 0), Duration.ofMinutes(90)); // 08:00-09:30, aula 6

        AllocationCandidate a = new AllocationCandidate(EventTestData.occurrenceSlot(10L, event1, date, OccurrenceStatus.NEEDS_ROOM), 5L);
        AllocationCandidate b = new AllocationCandidate(EventTestData.occurrenceSlot(11L, event2, date, OccurrenceStatus.NEEDS_ROOM), 5L);
        AllocationCandidate d = new AllocationCandidate(EventTestData.occurrenceSlot(12L, event3, date, OccurrenceStatus.NEEDS_ROOM), 6L);

        RecurringEvent occupantEvent = EventTestData.recurringEvent(77L, LocalTime.of(8, 0), Duration.ofMinutes(90)); // choque total en aula 6, solape 90
        OccurrenceSlotDto occupantOcc = EventTestData.occurrenceSlot(20L, occupantEvent, date, OccurrenceStatus.NEEDS_ROOM);
        Allocation occupied = Allocation.builder().id(500L).occurrenceId(20L).classroomId(6L).build();
        when(occurrenceService.findSlotsBetween(any(), any())).thenReturn(List.of(occupantOcc));
        when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of(occupied));

        assertThatThrownBy(() -> validator.validateManualOverlap(List.of(a, b, d), null))
                .isInstanceOf(ReallocationConflictException.class);
    }

    // ---------- validateManualOverlap vs. validateStrictOverlap (tolerancia 0) ----------

    @Test
    @DisplayName("validateManualOverlap: solape de 5 minutos con margen 10 pasa con observación (tolerado)")
    void validateManualOverlapSolape5ConMargen10PasaConObservacion() {
        LocalDate date = futureDate(1);
        RecurringEvent event = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90)); // 08:00-09:30
        AllocationCandidate candidate = new AllocationCandidate(
                EventTestData.occurrenceSlot(10L, event, date, OccurrenceStatus.NEEDS_ROOM), 5L);

        RecurringEvent occupantEvent = EventTestData.recurringEvent(2L, LocalTime.of(9, 25), Duration.ofMinutes(60)); // 09:25-10:25, solape 5
        OccurrenceSlotDto occupantOcc = EventTestData.occurrenceSlot(20L, occupantEvent, date, OccurrenceStatus.NEEDS_ROOM);
        Allocation occupied = Allocation.builder().id(500L).occurrenceId(20L).classroomId(5L).build();
        when(occurrenceService.findSlotsBetween(any(), any())).thenReturn(List.of(occupantOcc));
        when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of(occupied));

        maxOverlapMinutes = 10;
        assertThatCode(() -> validator.validateManualOverlap(List.of(candidate), "aula ajena, autorizado"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateStrictOverlap: el mismo solape de 5 minutos que validateManualOverlap tolera con margen 10 acá lanza (tolerancia 0)")
    void validateStrictOverlapSolape5MinutosLanza() {
        LocalDate date = futureDate(1);
        RecurringEvent event = EventTestData.recurringEvent(1L, LocalTime.of(8, 0), Duration.ofMinutes(90)); // 08:00-09:30
        AllocationCandidate candidate = new AllocationCandidate(
                EventTestData.occurrenceSlot(10L, event, date, OccurrenceStatus.NEEDS_ROOM), 5L);

        RecurringEvent occupantEvent = EventTestData.recurringEvent(2L, LocalTime.of(9, 25), Duration.ofMinutes(60)); // 09:25-10:25, solape 5
        OccurrenceSlotDto occupantOcc = EventTestData.occurrenceSlot(20L, occupantEvent, date, OccurrenceStatus.NEEDS_ROOM);
        Allocation occupied = Allocation.builder().id(500L).occurrenceId(20L).classroomId(5L).build();
        when(occurrenceService.findSlotsBetween(any(), any())).thenReturn(List.of(occupantOcc));
        when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of(occupied));

        assertThatThrownBy(() -> validator.validateStrictOverlap(List.of(candidate)))
                .isInstanceOf(ReallocationConflictException.class)
                .satisfies(ex -> {
                    List<OccurrenceConflictDto> conflicts = ((ReallocationConflictException) ex).getConflicts();
                    assertThat(conflicts).hasSize(1);
                    assertThat(conflicts.getFirst().overlapMinutes()).isEqualTo(5);
                });
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
