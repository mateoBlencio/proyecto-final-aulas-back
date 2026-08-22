package ar.edu.utn.frc.siga.allocation.service.command;

import java.time.LocalDate;
import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public sealed interface AllocationTarget {

    @NamedInterface("api")
    record Occurrences(List<Long> occurrenceIds) implements AllocationTarget {}

    @NamedInterface("api")
    record Event(Long eventId) implements AllocationTarget {}

    // A diferencia de Event, este target ignora el clamp de AllocationServiceImpl: trae sus
    // propias fechas y son ellas las que mandan. Por eso tampoco lleva @NamedInterface: solo lo
    // construye el mapper de este módulo.
    record EventRange(Long eventId, LocalDate from, LocalDate to) implements AllocationTarget {}
}
