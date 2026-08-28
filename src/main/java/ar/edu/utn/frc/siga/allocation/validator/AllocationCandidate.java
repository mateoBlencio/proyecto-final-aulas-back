package ar.edu.utn.frc.siga.allocation.validator;

import ar.edu.utn.frc.siga.common.util.TimeSpan;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;

import org.springframework.modulith.NamedInterface;

import java.time.LocalTime;

@NamedInterface("api")
public record AllocationCandidate(OccurrenceSlotDto occurrence, Long classroomId) implements TimeSpan {

    @Override
    public LocalTime startTime() {
        return occurrence.startTime();
    }

    @Override
    public LocalTime endTime() {
        return occurrence.endTime();
    }
}
