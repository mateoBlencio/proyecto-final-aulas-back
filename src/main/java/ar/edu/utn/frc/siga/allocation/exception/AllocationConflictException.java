package ar.edu.utn.frc.siga.allocation.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;
import org.springframework.modulith.NamedInterface;

import java.io.Serial;

@NamedInterface("api")
public class AllocationConflictException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AllocationConflictException(String detail) {
        super(HttpStatus.CONFLICT, "Allocation error", detail);
    }
}
