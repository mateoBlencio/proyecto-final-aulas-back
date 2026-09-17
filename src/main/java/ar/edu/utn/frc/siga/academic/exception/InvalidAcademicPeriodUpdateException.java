package ar.edu.utn.frc.siga.academic.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

public class InvalidAcademicPeriodUpdateException extends SigaAppException {

    public InvalidAcademicPeriodUpdateException(String detail) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Modificación de período académico inválida", detail);
    }
}
