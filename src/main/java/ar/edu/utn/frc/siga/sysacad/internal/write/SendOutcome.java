package ar.edu.utn.frc.siga.sysacad.internal.write;

import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadReviewReason;
import java.time.Duration;

public record SendOutcome(Kind kind, Duration retryAfter, SysacadReviewReason reviewReason, Failure failure) {

    public enum Kind {
        CONFIRMED, SKIPPED, REVIEW, SCHEDULE_FAILED, STOP_CONNECTION, STOP_RATE_LIMITED, STOP_UNAUTHORIZED,
        STOP_HOURS_CHANGED
    }

    /** Datos de un fallo por horario que {@code SysacadWriteRun} vuelca con o sin sumar intento. */
    public record Failure(String result, String error, String response, String errorId) {
    }

    static SendOutcome of(Kind kind) {
        return new SendOutcome(kind, null, null, null);
    }

    static SendOutcome review(SysacadReviewReason reason) {
        return new SendOutcome(Kind.REVIEW, null, reason, null);
    }

    static SendOutcome failed(Failure failure) {
        return new SendOutcome(Kind.SCHEDULE_FAILED, null, null, failure);
    }

    static SendOutcome rateLimited(Duration retryAfter) {
        return new SendOutcome(Kind.STOP_RATE_LIMITED, retryAfter, null, null);
    }

    static SendOutcome hoursChanged() {
        return new SendOutcome(Kind.STOP_HOURS_CHANGED, null, SysacadReviewReason.HOURS_CHANGED_AFTER_SEND, null);
    }
}
