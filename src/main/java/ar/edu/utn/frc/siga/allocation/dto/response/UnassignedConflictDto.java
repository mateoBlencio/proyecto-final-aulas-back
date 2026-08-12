package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;

public record UnassignedConflictDto(
        AcademicEventResponseDto event
) implements AllocationConflictDto {
}
