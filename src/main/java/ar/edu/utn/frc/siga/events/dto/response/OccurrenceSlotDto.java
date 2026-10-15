package ar.edu.utn.frc.siga.events.dto.response;

import ar.edu.utn.frc.siga.common.util.TimeSpan;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import org.springframework.modulith.NamedInterface;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@NamedInterface("api")
public record OccurrenceSlotDto(
        Long occurrenceId,
        Long eventId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        OccurrenceStatus status,
        Integer enrolled,
        Integer roomSlot
) implements TimeSpan {

    public OccurrenceSlotDto(Long occurrenceId, Long eventId, LocalDate date, LocalTime startTime,
                             LocalTime endTime, OccurrenceStatus status, Integer enrolled) {
        this(occurrenceId, eventId, date, startTime, endTime, status, enrolled, 1);
    }

    public boolean isPast() {
        return LocalDateTime.now().isAfter(date.atTime(startTime));
    }
}
