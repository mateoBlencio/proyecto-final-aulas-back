package ar.edu.utn.frc.classroom_allocation.space.exception;

import ar.edu.utn.frc.classroom_allocation.common.exception.ClassroomAllocationAppException;
import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ClassroomAllocationAppException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "Resource not found", message);
    }
}
