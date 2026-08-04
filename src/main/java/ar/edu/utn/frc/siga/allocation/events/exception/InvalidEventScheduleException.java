package ar.edu.utn.frc.siga.allocation.events.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * El horario de un evento (hora de inicio/fin) es inválido: fuera de la ventana horaria
 * permitida, o la hora de fin no es posterior a la de inicio.
 */
public class InvalidEventScheduleException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidEventScheduleException(String detail) {
        super(HttpStatus.BAD_REQUEST, "Invalid event schedule", detail);
    }
}
