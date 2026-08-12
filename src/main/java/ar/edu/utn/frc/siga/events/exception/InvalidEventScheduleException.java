package ar.edu.utn.frc.siga.events.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class InvalidEventScheduleException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidEventScheduleException(String detail) {
        super(HttpStatus.BAD_REQUEST, "Invalid event schedule", detail);
    }
}
