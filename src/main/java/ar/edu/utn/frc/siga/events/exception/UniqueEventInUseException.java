package ar.edu.utn.frc.siga.events.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class UniqueEventInUseException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public UniqueEventInUseException(Long eventId) {
        super(HttpStatus.CONFLICT, "Unique event in use",
                "El evento único " + eventId + " todavía tiene aulas asignadas o pedidos de aula que lo referencian.");
    }
}
