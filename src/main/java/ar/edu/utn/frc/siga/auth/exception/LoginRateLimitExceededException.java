package ar.edu.utn.frc.siga.auth.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

public class LoginRateLimitExceededException extends SigaAppException {

    public LoginRateLimitExceededException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts",
                "Demasiados intentos fallidos. Intente nuevamente más tarde");
    }
}
