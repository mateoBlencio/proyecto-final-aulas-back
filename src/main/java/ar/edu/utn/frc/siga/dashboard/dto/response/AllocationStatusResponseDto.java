package ar.edu.utn.frc.siga.dashboard.dto.response;

public record AllocationStatusResponseDto(
        long overlap, long overcrowded, long underused, long adequate, long total,
        Double overlapPercent, Double overcrowdedPercent, Double underusedPercent, Double adequatePercent) {
}
