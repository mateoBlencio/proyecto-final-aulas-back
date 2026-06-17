package ar.edu.utn.frc.classroom_allocation.space.exception;

import ar.edu.utn.frc.classroom_allocation.common.exception.ClassroomAllocationAppException;
import org.springframework.http.HttpStatus;

public class SpaceDomainException extends ClassroomAllocationAppException {

    public SpaceDomainException(String message) {
        super(HttpStatus.BAD_REQUEST, "Space domain error", message);
    }
}
