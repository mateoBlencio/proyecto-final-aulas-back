package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;

import java.time.LocalDateTime;

/** Vista de respuesta de una {@code Allocation}: qué aula tiene asignada una occurrence, su origen ({@code source}) y auditoría básica. */
public record AllocationResponseDto(
        Long id,
        AllocationSource source,
        LocalDateTime createdAt,
        String observation,
        OccurrenceResponseDto occurrence,
        AcademicEventResponseDto event,
        ClassroomResponseDto classroom
) {
}
