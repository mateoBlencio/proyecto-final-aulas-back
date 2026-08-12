package ar.edu.utn.frc.siga.optimizer.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class SchedulingException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SchedulingException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "Solver error", message);
    }

    public SchedulingException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "Solver error", message, cause);
    }
}
