package ar.edu.utn.frc.siga.auth.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import java.io.Serial;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TooManyRequestsException(Duration retryAfter) {
        this(retryAfterSeconds(retryAfter));
    }

    private TooManyRequestsException(long seconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Too many requests",
                "Demasiadas solicitudes seguidas. Reintentá en " + seconds + " segundos.");
        withHeader(HttpHeaders.RETRY_AFTER, String.valueOf(seconds));
    }

    private static long retryAfterSeconds(Duration retryAfter) {
        return Math.max(1, retryAfter.toSeconds() + (retryAfter.toNanosPart() > 0 ? 1 : 0));
    }
}
