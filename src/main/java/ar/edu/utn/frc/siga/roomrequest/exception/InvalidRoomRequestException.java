package ar.edu.utn.frc.siga.roomrequest.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/** La solicitud no cumple una regla de negocio que Bean Validation no puede expresar. */
public class InvalidRoomRequestException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidRoomRequestException(String detail) {
        super(HttpStatus.BAD_REQUEST, "Invalid room request", detail);
    }
}
