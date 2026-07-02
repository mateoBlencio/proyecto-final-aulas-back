package ar.edu.utn.frc.siga.allocation.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

public class AllocationNotFoundException extends SigaAppException {

    public AllocationNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Allocation not found", "Allocation not found with id: " + id);
    }
}
