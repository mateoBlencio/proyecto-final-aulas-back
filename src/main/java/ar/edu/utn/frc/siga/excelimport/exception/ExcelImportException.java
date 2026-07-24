package ar.edu.utn.frc.siga.excelimport.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Falla el procesamiento de una fila o del archivo Excel durante la importación masiva.
 */
public class ExcelImportException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ExcelImportException(String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "Import error", message);
    }

    public ExcelImportException(String message, Throwable cause) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "Import error", message, cause);
    }
}
