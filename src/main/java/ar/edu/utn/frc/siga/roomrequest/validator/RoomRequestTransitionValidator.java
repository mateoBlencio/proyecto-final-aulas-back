package ar.edu.utn.frc.siga.roomrequest.validator;

import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestTransitionException;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import org.springframework.stereotype.Component;

/** Transiciones de estado de un pedido (preaprobar / cancelar). Aún sin endpoint que lo exponga. */
@Component
public class RoomRequestTransitionValidator {

    public void validateTransition(RoomRequestStatus current, RoomRequestStatus target) {
        if (!current.allows(target)) {
            throw new InvalidRoomRequestTransitionException(current, target);
        }
    }
}
