package ar.edu.utn.frc.siga.allocation.service.command;

import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public sealed interface AllocationTarget {

    record Occurrences(List<Long> occurrenceIds) implements AllocationTarget {}

    record Event(Long eventId) implements AllocationTarget {}
}
