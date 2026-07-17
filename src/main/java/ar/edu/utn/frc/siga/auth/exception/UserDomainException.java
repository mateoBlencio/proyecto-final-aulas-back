package ar.edu.utn.frc.siga.auth.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

/**
 * Violación de una regla de negocio del dominio de usuarios (alta, cambio de rol, habilitación).
 */
public class UserDomainException extends SigaAppException {

    private static final long serialVersionUID = 1L;

    public UserDomainException(String message) {
        super(HttpStatus.BAD_REQUEST, "User domain error", message);
    }
}
