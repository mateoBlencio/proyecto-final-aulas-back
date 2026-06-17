package ar.edu.utn.frc.classroom_allocation.solver.exception;

import ar.edu.utn.frc.classroom_allocation.common.exception.ClassroomAllocationAppException;
import org.springframework.http.HttpStatus;

public class InvalidAllocationRequestException extends ClassroomAllocationAppException {
    public InvalidAllocationRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "Invalid allocation request", message);
    }
}
