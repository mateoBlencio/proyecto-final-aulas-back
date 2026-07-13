package ar.edu.utn.frc.siga.solver.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

/**
 * Falla inesperada del solver al calcular una asignación automática.
 */
public class SchedulingException extends SigaAppException {

    private static final long serialVersionUID = 1L;

    public SchedulingException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "Solver error", message);
    }

    public SchedulingException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "Solver error", message, cause);
    }
}
