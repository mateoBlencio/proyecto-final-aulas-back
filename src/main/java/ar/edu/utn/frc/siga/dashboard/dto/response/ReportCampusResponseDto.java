package ar.edu.utn.frc.siga.dashboard.dto.response;

import java.util.List;

public record ReportCampusResponseDto(
        int roomCount, int availableDays,
        AllocationStatusResponseDto allocationStatus,
        Long unallocatedClasses,
        OccupancyResponseDto occupancy,
        List<HourlyOccupancyResponseDto> hourlyProfile,
        List<SourceBreakdownResponseDto> bySource,
        RoomRequestCountsResponseDto roomRequests,
        RoomRequestCountsResponseDto unassignedBuildingRoomRequests) {
}
