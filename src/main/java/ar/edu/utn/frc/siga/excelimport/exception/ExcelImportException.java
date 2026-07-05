package ar.edu.utn.frc.siga.excelimport.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

public class ExcelImportException extends SigaAppException {

    public ExcelImportException(String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "Import error", message);
    }

    public ExcelImportException(String message, Throwable cause) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "Import error", message, cause);
    }
}
