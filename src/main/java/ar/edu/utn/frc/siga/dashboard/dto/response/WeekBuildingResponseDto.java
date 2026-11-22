package ar.edu.utn.frc.siga.dashboard.dto.response;

import java.util.List;

public record WeekBuildingResponseDto(
        Long buildingId, Integer code, String name,
        OccupancyResponseDto occupancy,
        RoomsInUseResponseDto roomsInUseNow,
        AllocationStatusResponseDto allocationStatus,
        List<HourlyOccupancyResponseDto> hourlyProfile,
        OpenRoomRequestsResponseDto openRoomRequests) {
}
