package ar.edu.utn.frc.siga.dashboard.dto.response;

import java.util.List;

public record ReportBuildingResponseDto(
        Long buildingId, Integer code, String name,
        int roomCount, int availableDays,
        AllocationStatusResponseDto allocationStatus,
        OccupancyResponseDto occupancy,
        List<HourlyOccupancyResponseDto> hourlyProfile,
        List<SourceBreakdownResponseDto> bySource,
        RoomRequestCountsResponseDto roomRequests) {
}
