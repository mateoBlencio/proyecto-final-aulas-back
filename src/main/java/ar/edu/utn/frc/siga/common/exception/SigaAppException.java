package ar.edu.utn.frc.siga.common.exception;

import java.io.Serial;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base de las excepciones de dominio de la aplicación. Cada subclase representa
 * un error HTTP específico; {@link GlobalExceptionHandler} las traduce a {@code ProblemDetail}.
 */
@Getter
public abstract class SigaAppException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String title;
    private final transient Map<String, Object> properties = new LinkedHashMap<>();

    protected SigaAppException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    protected SigaAppException(HttpStatus status, String title, String detail, Throwable cause) {
        super(detail, cause);
        this.status = status;
        this.title = title;
    }

    /**
     * Agrega una propiedad extra que el handler global adjuntará al ProblemDetail.
     */
    protected SigaAppException withProperty(String key, Object value) {
        this.properties.put(key, value);
        return this;
    }

    /**
     * Propiedades adicionales para adjuntar al {@code ProblemDetail} de respuesta.
     */
    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(properties);
    }
}
