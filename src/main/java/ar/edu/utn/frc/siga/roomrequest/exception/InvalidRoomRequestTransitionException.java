package ar.edu.utn.frc.siga.roomrequest.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/** Se intentó un cambio de estado que la máquina de estados no permite. */
public class InvalidRoomRequestTransitionException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidRoomRequestTransitionException(RoomRequestStatus current, RoomRequestStatus target) {
        super(HttpStatus.CONFLICT, "Invalid room request transition",
                "No se puede pasar la solicitud de " + current + " a " + target + ".");
        withProperty("currentStatus", current);
        withProperty("targetStatus", target);
    }
}
