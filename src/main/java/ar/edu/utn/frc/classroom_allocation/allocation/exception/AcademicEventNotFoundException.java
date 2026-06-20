package ar.edu.utn.frc.classroom_allocation.allocation.exception;

import ar.edu.utn.frc.classroom_allocation.common.exception.ClassroomAllocationAppException;
import org.springframework.http.HttpStatus;

public class AcademicEventNotFoundException extends ClassroomAllocationAppException {

    public AcademicEventNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Academic event not found", "Academic event not found with id: " + id);
    }
}
