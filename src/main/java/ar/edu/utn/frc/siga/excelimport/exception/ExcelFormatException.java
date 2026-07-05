package ar.edu.utn.frc.siga.excelimport.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

public class ExcelFormatException extends SigaAppException {

    public ExcelFormatException(String message) {
        super(HttpStatus.BAD_REQUEST, "Invalid file format", message);
    }
}
