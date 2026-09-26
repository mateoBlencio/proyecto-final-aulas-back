package ar.edu.utn.frc.siga.preview.dto.response;

import java.time.LocalDate;
import java.util.List;

import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Aula sugerida por el solver para una reasignación, o NO_ROOM_AVAILABLE si ninguna cumple las reglas hard")
public record ReallocationSuggestionResponseDto(
        String suggestionId,
        Long eventId,
        List<Long> occurrenceIds,
        List<LocalDate> dates,
        SuggestionStatus status,
        ClassroomResponseDto classroom,
        int overcrowdedBy
) {
    public enum SuggestionStatus { SUGGESTED, NO_ROOM_AVAILABLE }
}
