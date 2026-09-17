package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;

import java.time.LocalDate;
import java.util.List;

public record NotPermittedConflictDto(
        AcademicEventResponseDto event,
        ClassroomResponseDto classroom,
        Long subjectId,
        List<LocalDate> dates
) implements AllocationConflictDto {
}
