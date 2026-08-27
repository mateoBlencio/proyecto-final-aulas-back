package ar.edu.utn.frc.siga.roomrequest.service;

import ar.edu.utn.frc.siga.roomrequest.dto.RoomRequestItemFilter;
import ar.edu.utn.frc.siga.roomrequest.dto.request.CreateRoomRequestDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemDetailDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemRowDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RoomRequestService {

    RoomRequestResponseDto create(CreateRoomRequestDto dto);

    Page<RoomRequestItemRowDto> findItems(RoomRequestItemFilter filter, Pageable pageable);

    RoomRequestItemDetailDto findItemById(Long itemId);
}
