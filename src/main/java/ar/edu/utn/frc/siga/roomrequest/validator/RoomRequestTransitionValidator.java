package ar.edu.utn.frc.siga.roomrequest.validator;

import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestTransitionException;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import org.springframework.stereotype.Component;

@Component
public class RoomRequestTransitionValidator {

    public void validateTransition(RoomRequestStatus current, RoomRequestStatus target) {
        if (!current.allows(target)) {
            throw new InvalidRoomRequestTransitionException(current, target);
        }
    }
}
