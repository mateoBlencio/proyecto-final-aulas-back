package ar.edu.utn.frc.siga.events.dto.response;

import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import org.springframework.modulith.NamedInterface;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Franja que ocupa una {@code Occurrence}: lo único que {@code allocation} necesita de un
 * evento para validar solapamiento/capacidad, sin tocar la entidad JPA (que vive en
 * {@code events}). {@code enrolled} viaja para que el cálculo de sobrecupo no dependa de una
 * segunda consulta al evento.
 */
@NamedInterface("api")
public record OccurrenceSlotDto(
        Long occurrenceId,
        Long eventId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        OccurrenceStatus status,
        Integer enrolled
) {
    /** true si ya pasó el momento de inicio (fecha + hora de inicio vs. ahora). */
    public boolean isPast() {
        return LocalDateTime.now().isAfter(date.atTime(startTime));
    }
}
