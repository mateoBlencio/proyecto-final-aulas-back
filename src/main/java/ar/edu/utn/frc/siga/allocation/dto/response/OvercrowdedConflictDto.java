package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;

import java.time.LocalDate;
import java.util.List;

public record OvercrowdedConflictDto(
        AcademicEventResponseDto event,
        ClassroomResponseDto classroom,
        int enrolled,
        int capacity,
        int overcrowdedBy,
        List<LocalDate> dates
) implements AllocationConflictDto {
}
