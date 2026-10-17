package ar.edu.utn.frc.siga.events.dto.response;

import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import org.springframework.modulith.NamedInterface;

import java.time.LocalDate;
import java.time.LocalTime;

@NamedInterface("api")
public record OccurrenceResponseDto(
        Long id,
        Long eventId,
        LocalDate date,
        OccurrenceStatus status,
        LocalTime startTime,
        LocalTime endTime,
        ReleaseReason releaseReason,
        String releaseNote,
        Long previousClassroomId
) {
}
