package ar.edu.utn.frc.siga.allocation.events.dto.response;

import ar.edu.utn.frc.siga.allocation.events.model.OccurrenceStatus;

import java.time.LocalDate;
import java.time.LocalTime;

/** Vista de respuesta de una {@code Occurrence}: fecha concreta de un evento, su estado y su horario (heredado del evento). */
public record OccurrenceResponseDto(
        Long id,
        Long eventId,
        LocalDate date,
        OccurrenceStatus status,
        LocalTime startTime,
        LocalTime endTime
) {
}
