package ar.edu.utn.frc.siga.allocation.dto.request;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record AllocationItemRequestDto(
        List<Long> occurrenceIds,
        Long eventId,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate from,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate to,
        @NotNull Long classroomId
) {
    @AssertTrue(message = "Debe indicar occurrenceIds o eventId, pero no ambos ni ninguno")
    private boolean isTargetValid() {
        boolean hasOccurrenceIds = occurrenceIds != null && !occurrenceIds.isEmpty();
        boolean hasEventId = eventId != null;
        return hasOccurrenceIds != hasEventId;
    }

    /** Un rango sin eventId es ambiguo, y un `to` sin `from` no define ningún rango. */
    @AssertTrue(message = "'from' y 'to' solo aplican junto a eventId, y 'to' requiere 'from'")
    private boolean isRangeValid() {
        boolean hasRange = from != null || to != null;
        if (hasRange && eventId == null) {
            return false;
        }
        return to == null || from != null;
    }
}
