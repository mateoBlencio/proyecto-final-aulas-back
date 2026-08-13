package ar.edu.utn.frc.siga.allocation.service.command;

import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public sealed interface AllocationTarget {

    @NamedInterface("api")
    record Occurrences(List<Long> occurrenceIds) implements AllocationTarget {}

    @NamedInterface("api")
    record Event(Long eventId) implements AllocationTarget {}
}
