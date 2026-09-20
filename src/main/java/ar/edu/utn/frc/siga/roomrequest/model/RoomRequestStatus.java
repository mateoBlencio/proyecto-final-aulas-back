package ar.edu.utn.frc.siga.roomrequest.model;

import java.util.Set;

public enum RoomRequestStatus {

    NEW,
    DERIVED_TO_BUILDING,
    IN_EVALUATION,
    RESOLVED,
    CANCELLED;

    private static final Set<RoomRequestStatus> FROM_NEW = Set.of(DERIVED_TO_BUILDING, IN_EVALUATION, CANCELLED);
    private static final Set<RoomRequestStatus> FROM_DERIVED_TO_BUILDING = Set.of(IN_EVALUATION, NEW, CANCELLED);
    private static final Set<RoomRequestStatus> FROM_IN_EVALUATION = Set.of(IN_EVALUATION, RESOLVED, CANCELLED);

    public boolean allows(RoomRequestStatus target) {
        return switch (this) {
            case NEW -> FROM_NEW.contains(target);
            case DERIVED_TO_BUILDING -> FROM_DERIVED_TO_BUILDING.contains(target);
            case IN_EVALUATION -> FROM_IN_EVALUATION.contains(target);
            case RESOLVED -> target == CANCELLED;
            case CANCELLED -> false;
        };
    }

    public boolean isCancelled() {
        return this == CANCELLED;
    }
}
