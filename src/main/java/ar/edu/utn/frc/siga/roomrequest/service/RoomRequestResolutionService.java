package ar.edu.utn.frc.siga.roomrequest.service;

import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;

public interface RoomRequestResolutionService {

    RoomRequestItemResponseDto cancel(Long itemId, String reason, String actor);
}
