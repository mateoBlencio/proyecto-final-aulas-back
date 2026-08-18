package ar.edu.utn.frc.siga.allocation.dto.request;

import java.util.List;

import jakarta.validation.constraints.AssertTrue;

public record DeallocationTargetRequestDto(
        List<Long> occurrenceIds,
        Long eventId
) {
    @AssertTrue(message = "Debe indicar occurrenceIds o eventId, pero no ambos ni ninguno")
    private boolean isTargetValid() {
        boolean hasOccurrenceIds = occurrenceIds != null && !occurrenceIds.isEmpty();
        boolean hasEventId = eventId != null;
        return hasOccurrenceIds != hasEventId;
    }
}
