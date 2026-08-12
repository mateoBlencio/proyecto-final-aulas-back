package ar.edu.utn.frc.siga.preview.dto.response;

import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;

import java.time.LocalDate;
import java.util.List;

public record PreviewItemDto(
        AcademicEventResponseDto event,
        List<LocalDate> occurrenceDates,
        ClassroomResponseDto classroom,
        int overcrowdedBy,
        boolean unchanged
) {
}
