package ar.edu.utn.frc.siga.allocation.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * {@code subjectId} y {@code commissionId} existen individualmente, pero esa comisión no
 * corresponde a esa materia (no hay un {@code SubjectCommission} que los vincule).
 */
public class InvalidCommissionForSubjectException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCommissionForSubjectException(String detail) {
        super(HttpStatus.BAD_REQUEST, "Invalid commission for subject", detail);
    }
}
