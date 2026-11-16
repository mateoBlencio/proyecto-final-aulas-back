package ar.edu.utn.frc.siga.allocation.dto.response;

public record OvercrowdedIssueDto(int enrolled, int capacity, int overcrowdedBy) implements OccurrenceIssueDto {
}
