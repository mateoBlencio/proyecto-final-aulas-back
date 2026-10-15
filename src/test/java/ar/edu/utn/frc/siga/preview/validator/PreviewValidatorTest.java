package ar.edu.utn.frc.siga.preview.validator;

import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.validator.AllocationCandidate;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewAllocationDto;
import ar.edu.utn.frc.siga.preview.dto.response.MoveConflictDto;
import ar.edu.utn.frc.siga.preview.service.impl.PlanningKey;
import ar.edu.utn.frc.siga.preview.dto.response.MoveConflictDto.ConflictOrigin;
import ar.edu.utn.frc.siga.preview.validator.PreviewValidator.ResolvedProposal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PreviewValidator")
class PreviewValidatorTest {

    private PreviewValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PreviewValidator();
    }


    @Test
    @DisplayName("validateNoDuplicateAllocations: eventId repetido lanza AllocationConflictException")
    void validateNoDuplicateAllocationsRepetidoLanza() {
        List<PreviewAllocationDto> allocations = List.of(
                new PreviewAllocationDto(1L, null, 5L), new PreviewAllocationDto(1L, null, 6L));

        assertThatThrownBy(() -> validator.validateNoDuplicateAllocations(allocations))
                .isInstanceOf(AllocationConflictException.class);
    }

    @Test
    @DisplayName("validateNoDuplicateAllocations: sin repetidos no lanza")
    void validateNoDuplicateAllocationsSinRepetidosNoLanza() {
        List<PreviewAllocationDto> allocations = List.of(
                new PreviewAllocationDto(1L, null, 5L), new PreviewAllocationDto(2L, null, 6L));

        assertThatCode(() -> validator.validateNoDuplicateAllocations(allocations)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateAllocationsBelongToPreview: evento ajeno al preview lanza AllocationConflictException")
    void validateAllocationsBelongToPreviewAjenoLanza() {
        List<PreviewAllocationDto> allocations = List.of(new PreviewAllocationDto(99L, null, 5L));

        assertThatThrownBy(() -> validator.validateAllocationsBelongToPreview(allocations, Set.of(new PlanningKey(1L, 1), new PlanningKey(2L, 1))))
                .isInstanceOf(AllocationConflictException.class);
    }

    @Test
    @DisplayName("validateAllocationsBelongToPreview: todos pertenecen al preview no lanza")
    void validateAllocationsBelongToPreviewTodosPertenecenNoLanza() {
        List<PreviewAllocationDto> allocations = List.of(new PreviewAllocationDto(1L, null, 5L));

        assertThatCode(() -> validator.validateAllocationsBelongToPreview(allocations, Set.of(new PlanningKey(1L, 1), new PlanningKey(2L, 1))))
                .doesNotThrowAnyException();
    }


    @Test
    @DisplayName("unresolvedConflicts: aula bloqueada por BD y aula bloqueada por propuesta del preview → un conflicto por aula, origin correcto")
    void unresolvedConflictsUnoPorAulaConOriginCorrecto() {
        LocalDate date = futureDate(1);
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(9, 30);
        OccupiedSlot dbSlot = new OccupiedSlot(5L, date, start, end, 99L, 500L);
        ResolvedProposal previewProposal = new ResolvedProposal(1L, 6L, List.of(date), start, end);

        List<MoveConflictDto> conflicts = validator.unresolvedConflicts(Set.of(5L, 6L), Set.of(date), start, end,
                List.of(dbSlot), List.of(previewProposal));

        assertThat(conflicts).hasSize(2);
        assertThat(conflicts).anySatisfy(c -> {
            assertThat(c.classroomId()).isEqualTo(5L);
            assertThat(c.origin()).isEqualTo(ConflictOrigin.DATABASE);
            assertThat(c.conflictingEventId()).isEqualTo(99L);
        });
        assertThat(conflicts).anySatisfy(c -> {
            assertThat(c.classroomId()).isEqualTo(6L);
            assertThat(c.origin()).isEqualTo(ConflictOrigin.PREVIEW);
            assertThat(c.conflictingEventId()).isEqualTo(1L);
        });
    }

    @Test
    @DisplayName("unresolvedConflicts: tope de un conflicto por aula candidata, aunque haya varias fechas bloqueadas")
    void unresolvedConflictsTopeUnoPorAula() {
        LocalDate date1 = futureDate(1);
        LocalDate date2 = futureDate(8);
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(9, 30);
        OccupiedSlot slot1 = new OccupiedSlot(5L, date1, start, end, 99L, 500L);
        OccupiedSlot slot2 = new OccupiedSlot(5L, date2, start, end, 98L, 501L);

        List<MoveConflictDto> conflicts = validator.unresolvedConflicts(Set.of(5L), Set.of(date1, date2), start, end,
                List.of(slot1, slot2), List.of());

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.getFirst().date()).isEqualTo(date1);
    }

    @Test
    @DisplayName("unresolvedConflicts: aula libre (sin BD ni preview) no aparece en el resultado")
    void unresolvedConflictsAulaLibreNoAparece() {
        LocalDate date = futureDate(1);
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(9, 30);
        OccupiedSlot dbSlot = new OccupiedSlot(5L, date, start, end, 99L, 500L);

        List<MoveConflictDto> conflicts = validator.unresolvedConflicts(Set.of(5L, 7L), Set.of(date), start, end,
                List.of(dbSlot), List.of());

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.getFirst().classroomId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("unresolvedConflicts: propuesta unresolved (classroomId null) no bloquea ni rompe")
    void unresolvedConflictsIgnoraPropuestasSinAula() {
        LocalDate date = futureDate(1);
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(9, 30);
        ResolvedProposal sinAula = new ResolvedProposal(2L, null, List.of(date), start, end);

        List<MoveConflictDto> conflicts = validator.unresolvedConflicts(Set.of(6L), Set.of(date), start, end,
                List.of(), List.of(sinAula));

        assertThat(conflicts).isEmpty();
    }


    @Test
    @DisplayName("validateNoDuplicateAllocations: mismo evento y mismo roomSlot repetidos lanza AllocationConflictException")
    void validateNoDuplicateAllocationsMismoEventoYRoomSlotLanza() {
        List<PreviewAllocationDto> allocations = List.of(
                new PreviewAllocationDto(1L, 2, 5L), new PreviewAllocationDto(1L, 2, 6L));

        assertThatThrownBy(() -> validator.validateNoDuplicateAllocations(allocations))
                .isInstanceOf(AllocationConflictException.class);
    }

    @Test
    @DisplayName("validateNoDuplicateAllocations: mismo evento con roomSlot distinto no es duplicado")
    void validateNoDuplicateAllocationsRoomSlotDistintoNoLanza() {
        List<PreviewAllocationDto> allocations = List.of(
                new PreviewAllocationDto(1L, 1, 5L), new PreviewAllocationDto(1L, 2, 6L));

        assertThatCode(() -> validator.validateNoDuplicateAllocations(allocations)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateNoDuplicateAllocations: roomSlot null equivale a 1")
    void validateNoDuplicateAllocationsRoomSlotNullEquivaleAUno() {
        List<PreviewAllocationDto> allocations = List.of(
                new PreviewAllocationDto(1L, null, 5L), new PreviewAllocationDto(1L, 1, 6L));

        assertThatThrownBy(() -> validator.validateNoDuplicateAllocations(allocations))
                .isInstanceOf(AllocationConflictException.class);
    }

    @Test
    @DisplayName("validateAllocationsBelongToPreview: roomSlot que el preview no planificó lanza aunque el evento sí esté")
    void validateAllocationsBelongToPreviewRoomSlotAjenoLanza() {
        List<PreviewAllocationDto> allocations = List.of(new PreviewAllocationDto(1L, 2, 5L));

        assertThatThrownBy(() -> validator.validateAllocationsBelongToPreview(allocations, Set.of(new PlanningKey(1L, 1))))
                .isInstanceOf(AllocationConflictException.class)
                .hasMessageContaining("1:2");
    }

    @Test
    @DisplayName("validateAllocationsBelongToPreview: roomSlot null se resuelve contra la clave con roomSlot 1")
    void validateAllocationsBelongToPreviewRoomSlotNullEsUno() {
        List<PreviewAllocationDto> allocations = List.of(new PreviewAllocationDto(1L, null, 5L));

        assertThatCode(() -> validator.validateAllocationsBelongToPreview(allocations, Set.of(new PlanningKey(1L, 1))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateNoSiblingRoomClash: dos roomSlot del mismo evento en la misma aula y fecha lanza AllocationConflictException")
    void validateNoSiblingRoomClashMismaAulaYFechaLanza() {
        LocalDate date = futureDate(3);
        List<AllocationCandidate> candidates = List.of(
                candidate(10L, 1L, date, 1, 5L), candidate(11L, 1L, date, 2, 5L));

        assertThatThrownBy(() -> validator.validateNoSiblingRoomClash(candidates))
                .isInstanceOf(AllocationConflictException.class);
    }

    @Test
    @DisplayName("validateNoSiblingRoomClash: dos roomSlot del mismo evento en aulas distintas no lanza")
    void validateNoSiblingRoomClashAulasDistintasNoLanza() {
        LocalDate date = futureDate(3);
        List<AllocationCandidate> candidates = List.of(
                candidate(10L, 1L, date, 1, 5L), candidate(11L, 1L, date, 2, 6L));

        assertThatCode(() -> validator.validateNoSiblingRoomClash(candidates)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateNoSiblingRoomClash: misma aula en fechas distintas no lanza")
    void validateNoSiblingRoomClashFechasDistintasNoLanza() {
        List<AllocationCandidate> candidates = List.of(
                candidate(10L, 1L, futureDate(3), 1, 5L), candidate(11L, 1L, futureDate(10), 2, 5L));

        assertThatCode(() -> validator.validateNoSiblingRoomClash(candidates)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateNoSiblingRoomClash: eventos distintos en la misma aula y fecha no son de este validador")
    void validateNoSiblingRoomClashEventosDistintosNoLanza() {
        LocalDate date = futureDate(3);
        List<AllocationCandidate> candidates = List.of(
                candidate(10L, 1L, date, 1, 5L), candidate(20L, 2L, date, 1, 5L));

        assertThatCode(() -> validator.validateNoSiblingRoomClash(candidates)).doesNotThrowAnyException();
    }

    private AllocationCandidate candidate(Long occurrenceId, Long eventId, LocalDate date, int roomSlot,
            Long classroomId) {
        return new AllocationCandidate(new OccurrenceSlotDto(occurrenceId, eventId, date, LocalTime.of(8, 0),
                LocalTime.of(9, 30), OccurrenceStatus.NEEDS_ROOM, 30, roomSlot), classroomId);
    }

    private LocalDate futureDate(int daysFromNow) {
        return LocalDate.now().plusDays(daysFromNow);
    }

    @Test
    @DisplayName("roomOverlaps: libre, 20 min y 90 min con tope 40 devuelve las dos primeras ordenadas y descarta la tercera")
    void roomOverlapsDescartaLasQueSuperanElTope() {
        LocalDate date = LocalDate.of(2026, 3, 2);
        List<OccupiedSlot> occupancy = List.of(
                new OccupiedSlot(2L, date, LocalTime.of(7, 0), LocalTime.of(8, 20), 50L, 500L),
                new OccupiedSlot(3L, date, LocalTime.of(7, 0), LocalTime.of(9, 30), 51L, 501L),
                new OccupiedSlot(3L, date.plusDays(1), LocalTime.of(8, 0), LocalTime.of(10, 0), 52L, 502L));

        List<PreviewValidator.RoomOverlap> overlaps = validator.roomOverlaps(Set.of(3L, 2L, 1L), date,
                LocalTime.of(8, 0), LocalTime.of(10, 0), occupancy, List.of(), 40);

        assertThat(overlaps).containsExactly(
                new PreviewValidator.RoomOverlap(1L, 0, null),
                new PreviewValidator.RoomOverlap(2L, 20, 50L));
    }

    @Test
    @DisplayName("roomOverlaps: el solape contra una propuesta del preview se informa con su evento")
    void roomOverlapsContraPropuestaDelPreview() {
        LocalDate date = LocalDate.of(2026, 3, 2);
        ResolvedProposal proposal = new ResolvedProposal(60L, 1L, List.of(date),
                LocalTime.of(9, 30), LocalTime.of(11, 0));

        List<PreviewValidator.RoomOverlap> overlaps = validator.roomOverlaps(Set.of(1L), date,
                LocalTime.of(8, 0), LocalTime.of(10, 0), List.of(), List.of(proposal), 40);

        assertThat(overlaps).containsExactly(new PreviewValidator.RoomOverlap(1L, 30, 60L));
    }
}
