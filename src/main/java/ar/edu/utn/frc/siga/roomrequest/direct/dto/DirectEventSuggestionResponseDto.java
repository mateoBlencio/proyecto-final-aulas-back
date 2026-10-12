package ar.edu.utn.frc.siga.roomrequest.direct.dto;

import ar.edu.utn.frc.siga.roomrequest.dto.response.AllowedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestSuggestionResponseDto.SuggestionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Aulas sugeridas por el algoritmo para un borrador de evento; NO_ROOM_AVAILABLE si ninguna cumple las reglas hard")
public record DirectEventSuggestionResponseDto(
        String suggestionId,
        SuggestionStatus status,
        List<AllowedClassroomDto> classrooms,
        int overcrowdedBy
) {}
