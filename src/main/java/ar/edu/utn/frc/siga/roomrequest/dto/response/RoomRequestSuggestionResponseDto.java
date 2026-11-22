package ar.edu.utn.frc.siga.roomrequest.dto.response;

import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Aulas sugeridas por el algoritmo para un pedido; NO_ROOM_AVAILABLE si ninguna cumple las reglas hard")
public record RoomRequestSuggestionResponseDto(
        String suggestionId,
        Long itemId,
        SuggestionStatus status,
        List<ClassroomResponseDto> classrooms,
        int overcrowdedBy
) {
    public enum SuggestionStatus { SUGGESTED, PARTIAL, NO_ROOM_AVAILABLE }
}
