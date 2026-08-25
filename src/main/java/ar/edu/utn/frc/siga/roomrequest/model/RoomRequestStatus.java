package ar.edu.utn.frc.siga.roomrequest.model;

import java.util.Set;

/**
 * Estados de un pedido ({@link RoomRequestItem}); la cabecera no tiene estado propio.
 * Al agregar un valor, actualizar el check constraint {@code chk_solicitud_item_estado} en dev y en test.
 */
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
