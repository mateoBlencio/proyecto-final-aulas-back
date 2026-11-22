package ar.edu.utn.frc.siga.dashboard.dto.response;

public record HourlyOccupancyResponseDto(
        int hour, long occupiedRoomMinutes, long availableRoomMinutes, Double occupancyPercent) {
}
