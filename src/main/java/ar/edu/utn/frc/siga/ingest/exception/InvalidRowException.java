package ar.edu.utn.frc.siga.ingest.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class InvalidRowException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidRowException(String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "Import error", message);
    }

    public InvalidRowException(String message, Throwable cause) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "Import error", message, cause);
    }
}
