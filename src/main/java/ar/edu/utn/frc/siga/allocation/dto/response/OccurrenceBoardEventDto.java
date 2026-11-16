package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;

import java.util.List;

public record OccurrenceBoardEventDto(
        AcademicEventResponseDto event,
        OccurrenceBoardSummaryDto summary,
        List<OccurrenceBoardDateDto> occurrences
) {
}
