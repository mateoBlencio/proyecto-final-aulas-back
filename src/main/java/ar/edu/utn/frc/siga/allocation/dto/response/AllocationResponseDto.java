package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;

import java.time.LocalDateTime;

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
