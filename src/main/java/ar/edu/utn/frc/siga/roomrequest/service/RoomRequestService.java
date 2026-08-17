package ar.edu.utn.frc.siga.roomrequest.service;

import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestResponseDto;

public interface RoomRequestService {

    RoomRequestResponseDto create(CreateRoomRequestDto dto);
}
