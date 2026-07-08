package ar.edu.utn.frc.siga.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidDateRangeException extends SigaAppException {

    public InvalidDateRangeException(String detail) {
        super(HttpStatus.BAD_REQUEST, "Invalid date range", detail);
    }
}