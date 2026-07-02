package ar.edu.utn.frc.siga.allocation.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

public class OccurrenceNotFoundException extends SigaAppException {

    public OccurrenceNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Occurrence not found", "Occurrence not found with id: " + id);
    }
}
