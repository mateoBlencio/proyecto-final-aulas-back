package ar.edu.utn.frc.siga.allocation.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.modulith.NamedInterface;

import java.io.Serial;
import java.time.LocalDate;
import java.util.List;

@Getter
@NamedInterface("api")
public final class MirrorConsolidationNotPossibleException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public MirrorConsolidationNotPossibleException(List<LocalDate> dates) {
        super(HttpStatus.CONFLICT, "Mirror consolidation not possible",
                "No se puede liberar solo la clase seleccionada: en las fechas " + dates
                        + " las otras aulas de la clase no están disponibles. Liberá la clase completa.");
        withProperty("dates", dates);
    }
}
