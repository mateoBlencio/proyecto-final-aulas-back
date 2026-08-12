package ar.edu.utn.frc.siga.allocation.exception;

import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceConflictDto;
import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.io.Serial;
import java.util.List;

/**
 * Una reasignación en masa no puede aplicarse porque una o más ocurrencias solapan
 * con asignaciones existentes. Lleva el detalle de cuáles.
 */
@Getter
public final class ReallocationConflictException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<OccurrenceConflictDto> conflicts;

    public ReallocationConflictException(List<OccurrenceConflictDto> conflicts) {
        super(HttpStatus.CONFLICT, "Reallocation conflict",
                "No se puede reasignar: " + conflicts.size() + " ocurrencia(s) solapan con asignaciones existentes.");
        this.conflicts = conflicts;
        withProperty("conflicts", conflicts);
    }
}
