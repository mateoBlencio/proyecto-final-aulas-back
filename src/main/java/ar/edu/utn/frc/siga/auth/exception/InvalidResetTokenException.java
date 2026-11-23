package ar.edu.utn.frc.siga.auth.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import java.io.Serial;
import org.springframework.http.HttpStatus;

public class InvalidResetTokenException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidResetTokenException() {
        super(HttpStatus.BAD_REQUEST, "Invalid reset token", "El enlace no es válido o venció. Pedí uno nuevo.");
    }
}
