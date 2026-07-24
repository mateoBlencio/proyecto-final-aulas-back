package ar.edu.utn.frc.siga.allocation.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Una asignación manual no puede aplicarse porque el aula ya está ocupada
 * en ese horario por otra ocurrencia.
 */
public class AllocationConflictException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AllocationConflictException(String detail) {
        super(HttpStatus.CONFLICT, "Allocation error", detail);
    }
}
