package ar.edu.utn.frc.siga.cameracount.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;
import org.springframework.modulith.NamedInterface;

import java.io.Serial;

@NamedInterface("api")
public class CameraCountUnavailableException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String TITLE = "Conteo por cámaras no disponible";

    public CameraCountUnavailableException(String detail) {
        super(HttpStatus.SERVICE_UNAVAILABLE, TITLE, detail);
    }

    public CameraCountUnavailableException(String detail, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, TITLE, detail, cause);
    }

    public static CameraCountUnavailableException retryAfter(String detail, String retryAfter) {
        CameraCountUnavailableException exception = new CameraCountUnavailableException(detail);
        exception.withHeader("Retry-After", retryAfter);
        return exception;
    }
}
