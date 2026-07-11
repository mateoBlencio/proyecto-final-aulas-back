package ar.edu.utn.frc.siga.allocation.dto.response;

import java.util.List;

/**
 * Los tres listados de problemas de asignación de aulas para el rango consultado:
 * eventos sin aula, aulas con sobrecupo y superposiciones de horario-aula.
 */
public record AllocationProblemsResponseDto(
        List<AcademicEventResponseDto> unassigned,
        List<OvercrowdedAllocationDto> overcrowded,
        List<ClassroomOverlapDto> overlaps
) {
}
