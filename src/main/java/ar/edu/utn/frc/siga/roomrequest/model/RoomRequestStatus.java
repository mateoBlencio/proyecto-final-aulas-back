package ar.edu.utn.frc.siga.roomrequest.model;

import java.util.Set;

/**
 * Estados de un pedido dentro de una solicitud de aula ({@link RoomRequestItem}).
 * La cabecera no tiene estado: cada pedido se decide por separado.
 *
 * <p>Set inicial, pensado para ampliarse. Al agregar un valor hay que actualizar
 * el check constraint {@code chk_solicitud_item_estado} en la base de dev
 * <b>y</b> en la de test.
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
