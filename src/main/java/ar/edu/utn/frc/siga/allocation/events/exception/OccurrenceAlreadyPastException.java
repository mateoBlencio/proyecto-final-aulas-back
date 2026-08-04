package ar.edu.utn.frc.siga.allocation.events.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/** La occurrence ya ocurrió (fecha + hora de inicio ya pasó): no se puede modificar ni cancelar. */
public class OccurrenceAlreadyPastException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OccurrenceAlreadyPastException(String detail) {
        super(HttpStatus.CONFLICT, "Occurrence already past", detail);
    }
}
