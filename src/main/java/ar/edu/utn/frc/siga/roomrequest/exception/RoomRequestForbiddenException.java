package ar.edu.utn.frc.siga.roomrequest.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class RoomRequestForbiddenException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RoomRequestForbiddenException(String action) {
        super(HttpStatus.FORBIDDEN, "Room request action forbidden",
                "Tu rol no puede hacer '" + action + "' sobre este pedido en su estado o edificio actual.");
        withProperty("action", action);
    }
}
