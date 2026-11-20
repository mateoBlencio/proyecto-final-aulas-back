package ar.edu.utn.frc.siga.allocation.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

public class SuspendedEventAllocationException extends SigaAppException {

    public SuspendedEventAllocationException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Evento suspendido",
                "La materia, comisión, plan o especialidad de este evento está dada de baja.");
    }
}
