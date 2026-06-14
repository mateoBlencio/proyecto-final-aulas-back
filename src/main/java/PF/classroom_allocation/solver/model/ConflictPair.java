package PF.classroom_allocation.solver.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class ConflictPair {

    String eventIdA;
    String eventIdB;

    // Par simétrico: involucra al evento sin importar el orden
    public boolean involves(String eventId) {
        return eventIdA.equals(eventId) || eventIdB.equals(eventId);
    }

    // Devuelve el ID del otro evento del par
    public String otherEventId(String eventId) {
        if (eventIdA.equals(eventId)) return eventIdB;
        if (eventIdB.equals(eventId)) return eventIdA;
        throw new IllegalArgumentException("Event not in pair: " + eventId);
    }

    // (A,B) == (B,A)
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ConflictPair p)) return false;
        return (eventIdA.equals(p.eventIdA) && eventIdB.equals(p.eventIdB))
                || (eventIdA.equals(p.eventIdB) && eventIdB.equals(p.eventIdA));
    }

    @Override
    public int hashCode() {
        return eventIdA.hashCode() ^ eventIdB.hashCode();
    }
}
