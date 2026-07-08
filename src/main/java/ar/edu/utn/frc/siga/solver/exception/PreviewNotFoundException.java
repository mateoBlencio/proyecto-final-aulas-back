package ar.edu.utn.frc.siga.solver.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

/**
 * Preview inexistente o expirada. 410 GONE: el cliente debe re-generar el preview
 * para trabajar contra el estado actual de la BD.
 */
public class PreviewNotFoundException extends SigaAppException {
    public PreviewNotFoundException(String previewId) {
        super(HttpStatus.GONE, "Preview no disponible",
                "El preview '" + previewId + "' no existe o expiró. Generá uno nuevo.");
    }
}
