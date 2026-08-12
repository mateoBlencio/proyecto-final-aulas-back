package ar.edu.utn.frc.siga.allocation.dto.response;

/** Occurrence liberada por un {@code deallocate} y el aula que tenía hasta el momento de liberarla. */
public record DeallocatedOccurrenceDto(Long occurrenceId, Integer classroomId) {
}
