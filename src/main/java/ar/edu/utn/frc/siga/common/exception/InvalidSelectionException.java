package ar.edu.utn.frc.siga.common.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * La selección de eventos de un request es inválida (ej. ni {@code eventIds} ni
 * {@code selectAll}, o ambos a la vez).
 */
public class InvalidSelectionException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidSelectionException(String detail) {
        super(HttpStatus.BAD_REQUEST, "Invalid selection", detail);
    }
}
