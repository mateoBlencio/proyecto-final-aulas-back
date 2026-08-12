package ar.edu.utn.frc.siga.space.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class SpaceDomainException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SpaceDomainException(String message) {
        super(HttpStatus.BAD_REQUEST, "Space domain error", message);
    }
}
