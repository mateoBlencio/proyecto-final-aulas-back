package ar.edu.utn.frc.siga.allocation.service.command;

import java.util.List;

import org.springframework.modulith.NamedInterface;

/**
 * Cómo se nombra el conjunto de ocurrencias sobre las que opera un verbo de asignación.
 * Saca del nombre del método las tres formas de direccionar (una occurrence, varias, o
 * todas las de un evento) y las vuelve un parámetro: operar por evento pasa a ser de
 * primera clase. Sin {@code from}/{@code to}: el clamp lo aplica el servicio según el
 * {@code source} (ver {@code AllocationTargetResolver}).
 */
@NamedInterface("api")
public sealed interface AllocationTarget {

    /** Occurrences puntuales. Lista de 1 = operación individual. */
    record Occurrences(List<Long> occurrenceIds) implements AllocationTarget {}

    /** Todas las occurrences del evento, desde la primera fecha modificable. */
    record Event(Long eventId) implements AllocationTarget {}
}
