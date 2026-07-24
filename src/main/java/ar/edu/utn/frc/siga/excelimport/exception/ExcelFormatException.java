package ar.edu.utn.frc.siga.excelimport.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * El archivo Excel no respeta el formato de plantilla esperado (columnas, hoja, encabezados).
 */
public class ExcelFormatException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ExcelFormatException(String message) {
        super(HttpStatus.BAD_REQUEST, "Invalid file format", message);
    }
}
