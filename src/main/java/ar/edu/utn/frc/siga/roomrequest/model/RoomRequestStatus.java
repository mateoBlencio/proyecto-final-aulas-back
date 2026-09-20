package ar.edu.utn.frc.siga.roomrequest.model;

import java.util.Set;

public enum RoomRequestStatus {

    PENDING,
    DERIVED_TO_BUILDING,
    PRE_APPROVED,
    RESOLVED,
    CANCELLED;

    private static final Set<RoomRequestStatus> FROM_PENDING = Set.of(DERIVED_TO_BUILDING, PRE_APPROVED, CANCELLED);
    private static final Set<RoomRequestStatus> FROM_DERIVED_TO_BUILDING = Set.of(PRE_APPROVED, PENDING, CANCELLED);
    private static final Set<RoomRequestStatus> FROM_PRE_APPROVED = Set.of(PRE_APPROVED, RESOLVED, CANCELLED);

    public boolean allows(RoomRequestStatus target) {
        return switch (this) {
            case PENDING -> FROM_PENDING.contains(target);
            case DERIVED_TO_BUILDING -> FROM_DERIVED_TO_BUILDING.contains(target);
            case PRE_APPROVED -> FROM_PRE_APPROVED.contains(target);
            case RESOLVED -> target == CANCELLED;
            case CANCELLED -> false;
        };
    }

    public boolean isCancelled() {
        return this == CANCELLED;
    }
}
