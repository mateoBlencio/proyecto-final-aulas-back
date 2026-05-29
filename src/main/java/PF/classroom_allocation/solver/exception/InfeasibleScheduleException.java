package PF.classroom_allocation.solver.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
public class InfeasibleScheduleException extends RuntimeException {
    public InfeasibleScheduleException(String message) {
        super(message);
    }
}
