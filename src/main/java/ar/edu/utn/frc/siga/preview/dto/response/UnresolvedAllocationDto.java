package ar.edu.utn.frc.siga.preview.dto.response;

import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;

import java.time.LocalDate;
import java.util.List;

public record UnresolvedAllocationDto(
        AcademicEventResponseDto event,
        List<LocalDate> dates,
        List<MoveConflictDto> conflicts
) {
}
