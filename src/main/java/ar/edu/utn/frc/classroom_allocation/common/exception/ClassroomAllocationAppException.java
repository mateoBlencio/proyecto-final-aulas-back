package ar.edu.utn.frc.classroom_allocation.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class ClassroomAllocationAppException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    protected ClassroomAllocationAppException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    protected ClassroomAllocationAppException(HttpStatus status, String title, String detail, Throwable cause) {
        super(detail, cause);
        this.status = status;
        this.title = title;
    }
}
