package ar.edu.utn.frc.siga.dashboard.dto.response;

public record AllocationStatusResponseDto(
        double overlap, double overcrowded, double underused, double adequate, double total,
        Double overlapPercent, Double overcrowdedPercent, Double underusedPercent, Double adequatePercent) {
}
