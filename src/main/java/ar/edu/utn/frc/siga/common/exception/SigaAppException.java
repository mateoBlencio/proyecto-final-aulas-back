package ar.edu.utn.frc.siga.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class SigaAppException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    protected SigaAppException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    protected SigaAppException(HttpStatus status, String title, String detail, Throwable cause) {
        super(detail, cause);
        this.status = status;
        this.title = title;
    }
}
