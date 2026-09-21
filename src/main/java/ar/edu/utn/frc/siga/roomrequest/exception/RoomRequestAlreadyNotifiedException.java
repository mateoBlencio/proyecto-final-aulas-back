package ar.edu.utn.frc.siga.roomrequest.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class RoomRequestAlreadyNotifiedException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RoomRequestAlreadyNotifiedException(Long itemId) {
        super(HttpStatus.CONFLICT, "Room request already notified",
                "El pedido ya fue notificado al docente, es un estado terminal.");
        withProperty("itemId", itemId);
    }
}
