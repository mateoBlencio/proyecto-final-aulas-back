package ar.edu.utn.frc.siga.solver.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

public class SchedulingException extends SigaAppException {
    public SchedulingException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "Solver error", message);
    }

    public SchedulingException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "Solver error", message, cause);
    }
}
