package ar.edu.utn.frc.siga.cameracount.api;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class CameraCountFailedException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String TITLE = "Falló el conteo por cámaras";

    public CameraCountFailedException(String detail) {
        super(HttpStatus.BAD_GATEWAY, TITLE, detail);
    }

    public CameraCountFailedException(String detail, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, TITLE, detail, cause);
    }
}
