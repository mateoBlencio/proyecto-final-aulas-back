package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;

/** Evento con ocurrencias NEEDS_ROOM sin {@code Allocation} en el rango consultado. */
public record UnassignedConflictDto(
        AcademicEventResponseDto event
) implements AllocationConflictDto {
}
