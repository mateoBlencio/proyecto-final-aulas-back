package ar.edu.utn.frc.siga.space.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

public class SpaceDomainException extends SigaAppException {

    public SpaceDomainException(String message) {
        super(HttpStatus.BAD_REQUEST, "Space domain error", message);
    }
}
