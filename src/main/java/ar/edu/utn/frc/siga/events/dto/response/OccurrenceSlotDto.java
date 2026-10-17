package ar.edu.utn.frc.siga.events.dto.response;

import ar.edu.utn.frc.siga.common.util.TimeSpan;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
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
        ReleaseReason releaseReason,
        String releaseNote,
        Long previousClassroomId,
        Long mirrorOfOccurrenceId
) implements TimeSpan {
    public boolean isPast() {
        return LocalDateTime.now().isAfter(date.atTime(startTime));
    }
}
