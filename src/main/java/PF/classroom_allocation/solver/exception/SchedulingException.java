package PF.classroom_allocation.solver.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class SchedulingException extends RuntimeException {
    public SchedulingException(String message) {
        super(message);
    }
}
