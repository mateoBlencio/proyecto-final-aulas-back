package ar.edu.utn.frc.siga.solver.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

public class InvalidAllocationRequestException extends SigaAppException {
    public InvalidAllocationRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "Invalid allocation request", message);
    }
}
