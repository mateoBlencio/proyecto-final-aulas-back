package ar.edu.utn.frc.siga.allocation.events.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Falta una referencia académica obligatoria para el {@code eventType} indicado (Parcial,
 * Trabajo Práctico y Examen final requieren comisión; Otro no).
 */
public class MissingAcademicReferenceException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MissingAcademicReferenceException(String detail) {
        super(HttpStatus.BAD_REQUEST, "Missing academic reference", detail);
    }
}
