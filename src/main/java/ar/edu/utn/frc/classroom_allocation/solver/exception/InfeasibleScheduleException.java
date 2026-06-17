package ar.edu.utn.frc.classroom_allocation.solver.exception;

import ar.edu.utn.frc.classroom_allocation.common.exception.ClassroomAllocationAppException;
import org.springframework.http.HttpStatus;

public class InfeasibleScheduleException extends ClassroomAllocationAppException {
    public InfeasibleScheduleException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Infeasible schedule", message);
    }
}
