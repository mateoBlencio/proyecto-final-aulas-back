package ar.edu.utn.frc.siga.auth.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends SigaAppException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "Invalid credentials", "Email o contraseña incorrectos");
    }
}
