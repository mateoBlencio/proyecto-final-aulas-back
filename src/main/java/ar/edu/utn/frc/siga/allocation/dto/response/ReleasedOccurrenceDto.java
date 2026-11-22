package ar.edu.utn.frc.siga.allocation.dto.response;

import java.time.LocalDate;

import ar.edu.utn.frc.siga.events.model.ReleaseReason;

public record ReleasedOccurrenceDto(
        Long occurrenceId,
        Long eventId,
        LocalDate date,
        Long freedClassroomId,
        ReleaseReason reason,
        Long movedToClassroomId
) {

    public ReleasedOccurrenceDto(Long occurrenceId, Long eventId, LocalDate date, Long freedClassroomId,
                                 ReleaseReason reason) {
        this(occurrenceId, eventId, date, freedClassroomId, reason, null);
    }
}
