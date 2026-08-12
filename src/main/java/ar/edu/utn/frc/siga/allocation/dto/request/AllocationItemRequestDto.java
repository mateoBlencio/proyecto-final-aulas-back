package ar.edu.utn.frc.siga.allocation.dto.request;

import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * Un item del lote de asignación/reasignación: exactamente uno de {@code occurrenceIds} /
 * {@code eventId} debe venir informado — nombra el {@code AllocationTarget} (occurrences
 * puntuales o evento completo) al que se le quiere dar {@code classroomId}.
 */
public record AllocationItemRequestDto(
        List<Long> occurrenceIds,
        Long eventId,
        @NotNull Integer classroomId
) {
    @AssertTrue(message = "Debe indicar occurrenceIds o eventId, pero no ambos ni ninguno")
    private boolean isTargetValid() {
        boolean hasOccurrenceIds = occurrenceIds != null && !occurrenceIds.isEmpty();
        boolean hasEventId = eventId != null;
        return hasOccurrenceIds != hasEventId;
    }
}
