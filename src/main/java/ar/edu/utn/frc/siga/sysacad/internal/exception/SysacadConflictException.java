package ar.edu.utn.frc.siga.sysacad.internal.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import java.io.Serial;
import org.springframework.http.HttpStatus;

public class SysacadConflictException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SysacadConflictException(String title, String detail) {
        super(HttpStatus.CONFLICT, title, detail);
    }
}
