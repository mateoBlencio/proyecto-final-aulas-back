package ar.edu.utn.frc.siga.allocation.dto.response;

public record UnallocatedIssueDto(int missingRooms) implements OccurrenceIssueDto {
}
