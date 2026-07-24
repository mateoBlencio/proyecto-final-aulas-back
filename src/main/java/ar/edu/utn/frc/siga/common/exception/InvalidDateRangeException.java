package ar.edu.utn.frc.siga.common.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Un rango de fechas es inválido (por ejemplo, fin anterior a inicio).
 */
public class InvalidDateRangeException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidDateRangeException(String detail) {
        super(HttpStatus.BAD_REQUEST, "Invalid date range", detail);
    }
}