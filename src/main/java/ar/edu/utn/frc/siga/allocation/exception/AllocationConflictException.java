package ar.edu.utn.frc.siga.allocation.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

public class AllocationConflictException extends SigaAppException {

    public AllocationConflictException(String detail) {
        super(HttpStatus.CONFLICT, "Allocation error", detail);
    }
}
