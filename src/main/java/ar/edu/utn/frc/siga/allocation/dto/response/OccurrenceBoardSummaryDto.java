package ar.edu.utn.frc.siga.allocation.dto.response;

public record OccurrenceBoardSummaryDto(
        int total,
        int unallocated,
        int released,
        int overlap,
        int overcrowded,
        int notPermitted
) {
}
