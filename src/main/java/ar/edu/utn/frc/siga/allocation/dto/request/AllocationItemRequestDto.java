package ar.edu.utn.frc.siga.allocation.dto.request;

import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record AllocationItemRequestDto(
        List<Long> occurrenceIds,
        Long eventId,
        @NotNull Long classroomId
) {
    @AssertTrue(message = "Debe indicar occurrenceIds o eventId, pero no ambos ni ninguno")
    private boolean isTargetValid() {
        boolean hasOccurrenceIds = occurrenceIds != null && !occurrenceIds.isEmpty();
        boolean hasEventId = eventId != null;
        return hasOccurrenceIds != hasEventId;
    }
}
