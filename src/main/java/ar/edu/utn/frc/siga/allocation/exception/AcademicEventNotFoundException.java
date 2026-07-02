package ar.edu.utn.frc.siga.allocation.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

public class AcademicEventNotFoundException extends SigaAppException {

    public AcademicEventNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "Academic event not found", "Academic event not found with id: " + id);
    }
}
