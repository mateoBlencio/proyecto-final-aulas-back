package ar.edu.utn.frc.siga.settings.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class InvalidSettingValueException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidSettingValueException(String detail) {
        super(HttpStatus.BAD_REQUEST, "Valor de configuración inválido", detail);
    }
}
