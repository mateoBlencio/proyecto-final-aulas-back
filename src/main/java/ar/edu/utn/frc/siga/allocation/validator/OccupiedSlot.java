package ar.edu.utn.frc.siga.allocation.validator;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.modulith.NamedInterface;

import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.common.util.TimeSpan;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;

@NamedInterface("api")
public record OccupiedSlot(Integer classroomId, LocalDate date, LocalTime startTime, LocalTime endTime,
                            Long eventId, Long allocationId, Long occurrenceId) implements TimeSpan {

    /**
     * Para callers que solo necesitan saber que el aula está ocupada y no de qué ocurrencia viene.
     * La ocupación construida desde la base siempre usa {@link #from(Allocation, OccurrenceSlotDto)},
     * que sí la completa.
     */
    public OccupiedSlot(Integer classroomId, LocalDate date, LocalTime startTime, LocalTime endTime,
                        Long eventId, Long allocationId) {
        this(classroomId, date, startTime, endTime, eventId, allocationId, null);
    }

    public static OccupiedSlot from(Allocation a, OccurrenceSlotDto occurrence) {
        return new OccupiedSlot(a.getClassroomId(), occurrence.date(),
                occurrence.startTime(), occurrence.endTime(), occurrence.eventId(), a.getId(),
                occurrence.occurrenceId());
    }
}
