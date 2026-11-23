package ar.edu.utn.frc.siga.auth.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class RoleConflictException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RoleConflictException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Role conflict", message);
    }
}
