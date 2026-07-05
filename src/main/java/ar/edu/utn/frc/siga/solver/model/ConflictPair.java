package ar.edu.utn.frc.siga.solver.model;

import java.util.Objects;

/**
 * Par no ordenado de eventos en conflicto horario. El constructor normaliza el orden
 * (eventIdA <= eventIdB) para que equals/hashCode del record sean simétricos.
 */
public record ConflictPair(String eventIdA, String eventIdB) {

    public ConflictPair {
        Objects.requireNonNull(eventIdA, "eventIdA must not be null");
        Objects.requireNonNull(eventIdB, "eventIdB must not be null");
        if (eventIdA.equals(eventIdB)) {
            throw new IllegalArgumentException("A conflict pair requires two distinct events: " + eventIdA);
        }
        if (eventIdA.compareTo(eventIdB) > 0) {
            String tmp = eventIdA;
            eventIdA = eventIdB;
            eventIdB = tmp;
        }
    }
}
