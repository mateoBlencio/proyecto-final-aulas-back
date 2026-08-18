package ar.edu.utn.frc.siga.common.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

public class InvalidSelectionException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidSelectionException(String detail) {
        super(HttpStatus.BAD_REQUEST, "Invalid selection", detail);
    }
}
