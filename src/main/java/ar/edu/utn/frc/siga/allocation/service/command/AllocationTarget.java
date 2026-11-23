package ar.edu.utn.frc.siga.allocation.service.command;

import java.time.LocalDate;
import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public sealed interface AllocationTarget {

    @NamedInterface("api")
    record Occurrences(List<Long> occurrenceIds) implements AllocationTarget {}

    @NamedInterface("api")
    record Event(Long eventId, Integer roomSlot) implements AllocationTarget {
        public Event(Long eventId) {
            this(eventId, null);
        }
    }

    // A diferencia de Event, este target ignora el clamp de AllocationServiceImpl: trae sus
    // propias fechas y son ellas las que mandan.
    @NamedInterface("api")
    record EventRange(Long eventId, LocalDate from, LocalDate to, Integer roomSlot) implements AllocationTarget {
        public EventRange(Long eventId, LocalDate from, LocalDate to) {
            this(eventId, from, to, null);
        }
    }
}
