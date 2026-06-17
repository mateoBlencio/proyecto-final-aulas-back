package ar.edu.utn.frc.classroom_allocation.solver.exception;

import ar.edu.utn.frc.classroom_allocation.common.exception.ClassroomAllocationAppException;
import org.springframework.http.HttpStatus;

public class SchedulingException extends ClassroomAllocationAppException {
    public SchedulingException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "Solver error", message);
    }

    public SchedulingException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "Solver error", message, cause);
    }
}
