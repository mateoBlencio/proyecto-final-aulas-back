package ar.edu.utn.frc.siga.events.dto.response;

import ar.edu.utn.frc.siga.events.model.EventType;
import org.springframework.modulith.NamedInterface;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/** Snapshot histórico de un {@code RecurringEvent}: estado del evento recurrente en esa revisión, con IDs planos de materia/comisión. */
@NamedInterface("api")
public record RecurringEventHistorySnapshotDto(
        Long id,
        EventType type,
        Integer enrolled,
        LocalTime startTime,
        long durationMinutes,
        DayOfWeek dayOfWeek,
        LocalDate startDate,
        LocalDate endDate,
        Long subjectId,
        Long commissionId
) implements EventHistorySnapshotDto {
}
