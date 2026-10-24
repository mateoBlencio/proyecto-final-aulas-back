package ar.edu.utn.frc.siga.cameracount.api;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class InvalidCameraUrlException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCameraUrlException(String detail) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "URL de cámara inválida", detail);
    }
}
