package ar.edu.utn.frc.siga.auth.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class RoleDomainException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RoleDomainException(String message) {
        super(HttpStatus.BAD_REQUEST, "Role domain error", message);
    }
}
