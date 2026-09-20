package ar.edu.utn.frc.siga.roomrequest.service;

import ar.edu.utn.frc.siga.roomrequest.dto.response.AllowedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.CandidateBuildingDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.RoomRequestItemResponseDto;

import java.util.List;

public interface RoomRequestResolutionService {

    RoomRequestItemResponseDto assign(Long itemId, List<Long> classroomIds, String reason, String actor);

    RoomRequestItemResponseDto cancel(Long itemId, String reason, String actor);

    List<AllowedClassroomDto> findAllowedClassrooms(Long itemId);

    List<CandidateBuildingDto> findCandidateBuildings(Long itemId);

    RoomRequestItemResponseDto derive(Long itemId, Long buildingId, String actor);

    RoomRequestItemResponseDto returnItem(Long itemId, String reason, String actor);

    RoomRequestItemResponseDto notify(Long itemId, String actor);
}
