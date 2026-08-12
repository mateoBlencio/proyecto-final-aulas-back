package ar.edu.utn.frc.siga.preview.validator;

import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.preview.dto.request.PreviewAllocationDto;
import ar.edu.utn.frc.siga.preview.dto.response.MoveConflictDto;
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
    @DisplayName("validateNoDuplicateEventIds: eventId repetido lanza AllocationConflictException")
    void validateNoDuplicateEventIdsRepetidoLanza() {
        List<PreviewAllocationDto> allocations = List.of(
                new PreviewAllocationDto(1L, 5), new PreviewAllocationDto(1L, 6));

        assertThatThrownBy(() -> validator.validateNoDuplicateEventIds(allocations))
                .isInstanceOf(AllocationConflictException.class);
    }

    @Test
    @DisplayName("validateNoDuplicateEventIds: sin repetidos no lanza")
    void validateNoDuplicateEventIdsSinRepetidosNoLanza() {
        List<PreviewAllocationDto> allocations = List.of(
                new PreviewAllocationDto(1L, 5), new PreviewAllocationDto(2L, 6));

        assertThatCode(() -> validator.validateNoDuplicateEventIds(allocations)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateAllocationsBelongToPreview: evento ajeno al preview lanza AllocationConflictException")
    void validateAllocationsBelongToPreviewAjenoLanza() {
        List<PreviewAllocationDto> allocations = List.of(new PreviewAllocationDto(99L, 5));

        assertThatThrownBy(() -> validator.validateAllocationsBelongToPreview(allocations, Set.of(1L, 2L)))
                .isInstanceOf(AllocationConflictException.class);
    }

    @Test
    @DisplayName("validateAllocationsBelongToPreview: todos pertenecen al preview no lanza")
    void validateAllocationsBelongToPreviewTodosPertenecenNoLanza() {
        List<PreviewAllocationDto> allocations = List.of(new PreviewAllocationDto(1L, 5));

        assertThatCode(() -> validator.validateAllocationsBelongToPreview(allocations, Set.of(1L, 2L)))
                .doesNotThrowAnyException();
    }


    @Test
    @DisplayName("unresolvedConflicts: aula bloqueada por BD y aula bloqueada por propuesta del preview → un conflicto por aula, origin correcto")
    void unresolvedConflictsUnoPorAulaConOriginCorrecto() {
        LocalDate date = futureDate(1);
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(9, 30);
        OccupiedSlot dbSlot = new OccupiedSlot(5, date, start, end, 99L, 500L);
        ResolvedProposal previewProposal = new ResolvedProposal(1L, 6, List.of(date), start, end);

        List<MoveConflictDto> conflicts = validator.unresolvedConflicts(Set.of(5, 6), Set.of(date), start, end,
                List.of(dbSlot), List.of(previewProposal));

        assertThat(conflicts).hasSize(2);
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
    @DisplayName("unresolvedConflicts: tope de un conflicto por aula candidata, aunque haya varias fechas bloqueadas")
    void unresolvedConflictsTopeUnoPorAula() {
        LocalDate date1 = futureDate(1);
        LocalDate date2 = futureDate(8);
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(9, 30);
        OccupiedSlot slot1 = new OccupiedSlot(5, date1, start, end, 99L, 500L);
        OccupiedSlot slot2 = new OccupiedSlot(5, date2, start, end, 98L, 501L);

        List<MoveConflictDto> conflicts = validator.unresolvedConflicts(Set.of(5), Set.of(date1, date2), start, end,
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
        OccupiedSlot dbSlot = new OccupiedSlot(5, date, start, end, 99L, 500L);

        List<MoveConflictDto> conflicts = validator.unresolvedConflicts(Set.of(5, 7), Set.of(date), start, end,
                List.of(dbSlot), List.of());

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.getFirst().classroomId()).isEqualTo(5);
    }

    @Test
    @DisplayName("unresolvedConflicts: propuesta unresolved (classroomId null) no bloquea ni rompe")
    void unresolvedConflictsIgnoraPropuestasSinAula() {
        LocalDate date = futureDate(1);
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(9, 30);
        ResolvedProposal sinAula = new ResolvedProposal(2L, null, List.of(date), start, end);

        List<MoveConflictDto> conflicts = validator.unresolvedConflicts(Set.of(6), Set.of(date), start, end,
                List.of(), List.of(sinAula));

        assertThat(conflicts).isEmpty();
    }


    private LocalDate futureDate(int daysFromNow) {
        return LocalDate.now().plusDays(daysFromNow);
    }
}
