package ar.edu.utn.frc.siga.common.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class SigaAppException extends RuntimeException {

    private final HttpStatus status;
    private final String title;
    private final Map<String, Object> properties = new LinkedHashMap<>();

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

    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(properties);
    }
}
