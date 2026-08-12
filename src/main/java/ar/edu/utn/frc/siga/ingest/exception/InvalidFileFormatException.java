package ar.edu.utn.frc.siga.ingest.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class InvalidFileFormatException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidFileFormatException(String message) {
        super(HttpStatus.BAD_REQUEST, "Invalid file format", message);
    }
}
