package ar.edu.utn.frc.siga.solver.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

public class InfeasibleScheduleException extends SigaAppException {
    public InfeasibleScheduleException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Infeasible schedule", message);
    }
}
