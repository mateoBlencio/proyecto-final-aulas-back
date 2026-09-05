package ar.edu.utn.frc.siga.roomrequest.handler;

import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;

public interface RoomRequestTypeHandler {

    RoomRequestType type();

    void validate(CreateRoomRequestDto dto);

    RoomRequest assemble(CreateRoomRequestDto dto);
}
