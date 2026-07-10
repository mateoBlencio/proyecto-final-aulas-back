package ar.edu.utn.frc.siga.allocation.dto.response;

import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;

import java.time.LocalDate;
import java.time.LocalTime;

public record OccurrenceResponseDto(
        Long id,
        Long eventId,
        LocalDate date,
        OccurrenceStatus status,
        LocalTime startTime,
        LocalTime endTime
) {
}
