package ar.edu.utn.frc.siga.roomrequest.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class ExpiredSuggestionException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ExpiredSuggestionException(String suggestionId) {
        super(HttpStatus.GONE, "Sugerencia no disponible",
                "La sugerencia '" + suggestionId + "' no existe, expiró o el pedido cambió. Pedí una nueva.");
    }
}
