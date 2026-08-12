package ar.edu.utn.frc.siga.allocation.validator;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.modulith.NamedInterface;

import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.common.util.TimeSpan;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;

/** Franja ocupada, sea de BD o de un snapshot automático. */
@NamedInterface("api")
public record OccupiedSlot(Integer classroomId, LocalDate date, LocalTime startTime, LocalTime endTime,
                            Long eventId, Long allocationId) implements TimeSpan {

    public static OccupiedSlot from(Allocation a, OccurrenceSlotDto occurrence) {
        return new OccupiedSlot(a.getClassroomId(), occurrence.date(),
                occurrence.startTime(), occurrence.endTime(), occurrence.eventId(), a.getId());
    }
}
