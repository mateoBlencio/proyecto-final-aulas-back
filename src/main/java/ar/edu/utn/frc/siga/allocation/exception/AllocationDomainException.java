package ar.edu.utn.frc.siga.allocation.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

public class AllocationDomainException extends SigaAppException {

    public AllocationDomainException(String detail) {
        super(HttpStatus.CONFLICT, "Allocation error", detail);
    }
}
