package ar.edu.utn.frc.classroom_allocation.solver.model;

public record ConflictPair(String eventIdA, String eventIdB) {

    public boolean involves(String eventId) {
        return eventIdA.equals(eventId) || eventIdB.equals(eventId);
    }

    public String otherEventId(String eventId) {
        if (eventIdA.equals(eventId)) return eventIdB;
        if (eventIdB.equals(eventId)) return eventIdA;
        throw new IllegalArgumentException("Event not in pair: " + eventId);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ConflictPair(String idA, String idB))) return false;
        return (eventIdA.equals(idA) && eventIdB.equals(idB))
                || (eventIdA.equals(idB) && eventIdB.equals(idA));
    }

    @Override
    public int hashCode() {
        return eventIdA.hashCode() ^ eventIdB.hashCode();
    }
}
