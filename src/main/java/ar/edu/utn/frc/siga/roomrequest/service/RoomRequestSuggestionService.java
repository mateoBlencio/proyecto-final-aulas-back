package ar.edu.utn.frc.siga.roomrequest.service;

import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestSuggestionResponseDto;

import java.util.Collection;

public interface RoomRequestSuggestionService {

    RoomRequestSuggestionResponseDto suggest(Long itemId, Collection<Long> excludedClassroomIds, String actor);

    RoomRequestItemResponseDto confirm(Long itemId, String suggestionId, String reason, String actor);
}
