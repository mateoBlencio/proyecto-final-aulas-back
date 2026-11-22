package ar.edu.utn.frc.siga.preview.dto.request;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

@Schema(description = "Target a reasignar (occurrenceIds o eventId+from[+to]) + aulas a excluir de la sugerencia")
public record ReallocationSuggestionRequestDto(
        @Size(max = 200) List<Long> occurrenceIds,
        Long eventId,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate from,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate to,
        @Size(max = 200) List<Long> excludedClassroomIds
) {
    @AssertTrue(message = "Debe indicar occurrenceIds o eventId, pero no ambos ni ninguno")
    private boolean isTargetValid() {
        boolean hasOccurrenceIds = occurrenceIds != null && !occurrenceIds.isEmpty();
        boolean hasEventId = eventId != null;
        return hasOccurrenceIds != hasEventId;
    }

    /** 'from'/'to' solo aplican junto a eventId, y con eventId 'from' es obligatorio (a diferencia
     *  de AllocationItemRequestDto, acá no hay variante "todas las ocurrencias futuras"). */
    @AssertTrue(message = "'eventId' requiere 'from'; 'from' y 'to' solo aplican junto a eventId")
    private boolean isRangeValid() {
        if (eventId == null) {
            return from == null && to == null;
        }
        return from != null;
    }
}
