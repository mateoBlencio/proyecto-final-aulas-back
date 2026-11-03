package ar.edu.utn.frc.siga.sysacad.internal.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class CommissionMessageNotAllowedException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommissionMessageNotAllowedException() {
        super(HttpStatus.FORBIDDEN, "Envío no permitido",
                "Solo se puede enviar mensajes a la materia y comisión habilitadas para autogestión");
    }
}
