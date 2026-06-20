package ar.edu.utn.frc.classroom_allocation.allocation.exception;

import ar.edu.utn.frc.classroom_allocation.common.exception.ClassroomAllocationAppException;
import org.springframework.http.HttpStatus;

public class AllocationDomainException extends ClassroomAllocationAppException {

    public AllocationDomainException(String detail) {
        super(HttpStatus.CONFLICT, "Allocation error", detail);
    }
}
