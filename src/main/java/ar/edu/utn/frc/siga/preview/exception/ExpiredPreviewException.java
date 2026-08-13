package ar.edu.utn.frc.siga.preview.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class ExpiredPreviewException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ExpiredPreviewException(String previewId) {
        super(HttpStatus.GONE, "Preview no disponible",
                "El preview '" + previewId + "' no existe o expiró. Generá uno nuevo.");
    }
}
