package ar.edu.utn.frc.siga.common.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;
import java.util.Set;

public class InvalidSortPropertyException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidSortPropertyException(String property, Set<String> allowed) {
        super(HttpStatus.BAD_REQUEST, "Invalid sort property",
                "No se puede ordenar por '" + property + "'. Propiedades válidas: " + String.join(", ", allowed));
    }
}
