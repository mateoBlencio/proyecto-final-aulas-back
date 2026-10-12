package ar.edu.utn.frc.siga.auth.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class LoginRateLimitExceededException extends SigaAppException {

    private final long retryAfterSeconds;

    public LoginRateLimitExceededException(Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts",
                "Demasiados intentos fallidos. Intente nuevamente más tarde");
        this.retryAfterSeconds = Math.max(1, retryAfter.toSeconds() + (retryAfter.toNanosPart() > 0 ? 1 : 0));
        withProperty("retryAfterSeconds", retryAfterSeconds);
        withHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
