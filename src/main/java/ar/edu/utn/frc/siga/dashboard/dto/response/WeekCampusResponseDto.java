package ar.edu.utn.frc.siga.dashboard.dto.response;

import java.util.List;

public record WeekCampusResponseDto(
        OccupancyResponseDto occupancy,
        RoomsInUseResponseDto roomsInUseNow,
        AllocationStatusResponseDto allocationStatus,
        List<HourlyOccupancyResponseDto> hourlyProfile,
        Long unallocatedClasses,
        OpenRoomRequestsResponseDto openRoomRequests) {
}
