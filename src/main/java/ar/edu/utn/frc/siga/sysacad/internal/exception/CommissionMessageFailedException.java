package ar.edu.utn.frc.siga.sysacad.internal.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class CommissionMessageFailedException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommissionMessageFailedException(Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, "Falló el envío a SysAcad",
                "SysAcad no aceptó el mensaje a la comisión", cause);
    }
}
