package ar.edu.utn.frc.classroom_allocation.allocation.exception;

import ar.edu.utn.frc.classroom_allocation.common.exception.ClassroomAllocationAppException;
import org.springframework.http.HttpStatus;

public class OccurrenceNotFoundException extends ClassroomAllocationAppException {

    public OccurrenceNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Occurrence not found", "Occurrence not found with id: " + id);
    }
}
