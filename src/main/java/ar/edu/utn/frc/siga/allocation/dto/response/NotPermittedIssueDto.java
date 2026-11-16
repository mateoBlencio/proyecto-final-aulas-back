package ar.edu.utn.frc.siga.allocation.dto.response;

public record NotPermittedIssueDto(Long occurrenceId, Long classroomId) implements OccurrenceIssueDto {
}
