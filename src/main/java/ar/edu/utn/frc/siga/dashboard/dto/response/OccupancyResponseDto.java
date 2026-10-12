package ar.edu.utn.frc.siga.dashboard.dto.response;

public record OccupancyResponseDto(
        double occupiedHours, double availableHours, double freeHours, Double occupancyPercent) {
}
