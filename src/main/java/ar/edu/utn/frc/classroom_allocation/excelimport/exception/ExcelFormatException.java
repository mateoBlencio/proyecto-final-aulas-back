package ar.edu.utn.frc.classroom_allocation.excelimport.exception;

import ar.edu.utn.frc.classroom_allocation.common.exception.ClassroomAllocationAppException;
import org.springframework.http.HttpStatus;

public class ExcelFormatException extends ClassroomAllocationAppException {

    public ExcelFormatException(String message) {
        super(HttpStatus.BAD_REQUEST, "Invalid file format", message);
    }
}
