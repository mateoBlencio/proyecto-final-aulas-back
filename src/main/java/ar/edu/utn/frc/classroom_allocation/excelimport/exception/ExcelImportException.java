package ar.edu.utn.frc.classroom_allocation.excelimport.exception;

import ar.edu.utn.frc.classroom_allocation.common.exception.ClassroomAllocationAppException;
import org.springframework.http.HttpStatus;

public class ExcelImportException extends ClassroomAllocationAppException {

    public ExcelImportException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Import error", message);
    }

    public ExcelImportException(String message, Throwable cause) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Import error", message, cause);
    }
}
