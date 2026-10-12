package ar.edu.utn.frc.siga.dashboard.dto.response;

import java.util.List;

public record RoomRequestYearResponseDto(
        int year, boolean partial,
        RoomRequestCountsResponseDto campus,
        List<RoomRequestBuildingCountsResponseDto> buildings,
        RoomRequestCountsResponseDto unassignedBuilding) {
}
