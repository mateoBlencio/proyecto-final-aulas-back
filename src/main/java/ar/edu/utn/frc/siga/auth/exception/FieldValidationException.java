package ar.edu.utn.frc.siga.auth.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;
import java.util.List;
import java.util.Map;

public class FieldValidationException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public FieldValidationException(String field, String message) {
        this(field, List.of(message));
    }

    public FieldValidationException(String field, List<String> messages) {
        super(HttpStatus.BAD_REQUEST, "Validación fallida", "Uno o más campos no son válidos.");
        withProperty("errors", Map.of(field, List.copyOf(messages)));
    }
}
