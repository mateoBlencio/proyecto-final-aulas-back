package ar.edu.utn.frc.siga.events.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class OccurrenceAlreadyPastException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OccurrenceAlreadyPastException(String detail) {
        super(HttpStatus.CONFLICT, "Occurrence already past", detail);
    }
}
