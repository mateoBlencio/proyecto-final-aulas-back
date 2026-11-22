package ar.edu.utn.frc.siga.cameracount.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;
import org.springframework.modulith.NamedInterface;

import java.io.Serial;

@NamedInterface("api")
public class InvalidCameraUrlException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCameraUrlException(String detail) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "URL de cámara inválida", detail);
    }
}
