package ar.edu.utn.frc.siga.allocation.model;

import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import java.time.LocalDate;
import java.time.LocalTime;

/** One allocated occurrence matched by the period search. */
public record AllocationSearchRow(Long allocationId, Long occurrenceId, LocalDate date, Long eventId,
        EventType eventType, UniqueEventKind uniqueEventType, LocalTime startTime, int durationMinutes,
        String subject, String section, String specialtyName, Long classroomId, int classroomNumber,
        Long buildingId) {
}
