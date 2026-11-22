package ar.edu.utn.frc.siga.dashboard.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import java.io.Serial;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;

public class PeriodNotStartedException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PeriodNotStartedException(String period, LocalDate start) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Period not started",
                "El período " + period + " todavía no empezó: comienza el " + start);
    }
}
