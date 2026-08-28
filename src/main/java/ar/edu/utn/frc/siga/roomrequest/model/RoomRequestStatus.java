package ar.edu.utn.frc.siga.roomrequest.model;

import java.util.Set;

public enum RoomRequestStatus {

    PENDING,
    PRE_APPROVED,
    CANCELLED;

    private static final Set<RoomRequestStatus> FROM_PENDING = Set.of(PRE_APPROVED, CANCELLED);

    public boolean allows(RoomRequestStatus target) {
        return switch (this) {
            case PENDING -> FROM_PENDING.contains(target);
            case PRE_APPROVED -> target == CANCELLED;
            case CANCELLED -> false;
        };
    }

    public boolean isFinal() {
        return this == CANCELLED;
    }
}
