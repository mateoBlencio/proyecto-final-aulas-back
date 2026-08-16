package ar.edu.utn.frc.siga.roomrequest.service;

import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestResponseDto;

public interface RoomRequestService {

    RoomRequestResponseDto create(CreateRoomRequestDto dto);

    RoomRequestResponseDto findById(Long id);

    /**
     * Decide un pedido puntual. Los demás pedidos de la misma solicitud quedan
     * como estaban: el parcial de abril se puede resolver sin tocar el
     * recuperatorio de julio.
     */
    RoomRequestResponseDto preApproveItem(Long requestId, Long itemId, String decidedBy, String reason);

    RoomRequestResponseDto cancelItem(Long requestId, Long itemId, String decidedBy, String reason);
}
