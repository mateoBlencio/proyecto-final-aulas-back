package PF.classroom_allocation.solver.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidAllocationRequestException extends RuntimeException {
    public InvalidAllocationRequestException(String message) {
        super(message);
    }
}
