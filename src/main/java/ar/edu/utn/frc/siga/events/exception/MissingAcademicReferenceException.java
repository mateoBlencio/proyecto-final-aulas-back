package ar.edu.utn.frc.siga.events.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class MissingAcademicReferenceException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MissingAcademicReferenceException(String detail) {
        super(HttpStatus.BAD_REQUEST, "Missing academic reference", detail);
    }
}
