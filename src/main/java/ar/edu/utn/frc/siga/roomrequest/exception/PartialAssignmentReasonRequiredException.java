package ar.edu.utn.frc.siga.roomrequest.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class PartialAssignmentReasonRequiredException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PartialAssignmentReasonRequiredException() {
        super(HttpStatus.BAD_REQUEST, "Partial assignment reason required",
                "El motivo es obligatorio al asignar menos aulas de las pedidas.");
    }
}
