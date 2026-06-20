package ar.edu.utn.frc.classroom_allocation.allocation.exception;

import ar.edu.utn.frc.classroom_allocation.common.exception.ClassroomAllocationAppException;
import org.springframework.http.HttpStatus;

public class AllocationNotFoundException extends ClassroomAllocationAppException {

    public AllocationNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Allocation not found", "Allocation not found with id: " + id);
    }
}
