package ar.edu.utn.frc.siga.dashboard.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import java.io.Serial;
import java.time.YearMonth;
import org.springframework.http.HttpStatus;

public class MonthNotClosedException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MonthNotClosedException(YearMonth month) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Month not closed",
                "El mes " + month + " todavía no está cerrado: solo se regeneran informes de meses anteriores al actual");
    }
}
