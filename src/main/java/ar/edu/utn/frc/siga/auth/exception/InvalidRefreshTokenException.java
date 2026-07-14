package ar.edu.utn.frc.siga.auth.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends SigaAppException {

    public InvalidRefreshTokenException() {
        super(HttpStatus.UNAUTHORIZED, "Invalid refresh token", "El refresh token es inválido, expiró o ya fue utilizado");
    }
}
