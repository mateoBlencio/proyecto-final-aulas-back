package ar.edu.utn.frc.siga.allocation.exception;

import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceConflictDto;
import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Una reasignación en masa no puede aplicarse porque una o más ocurrencias solapan
 * con asignaciones existentes. Lleva el detalle de cuáles.
 */
@Getter
public class ReassignConflictException extends SigaAppException {

    private final transient List<OccurrenceConflictDto> conflicts;

    public ReassignConflictException(List<OccurrenceConflictDto> conflicts) {
        super(HttpStatus.CONFLICT, "Reassign conflict",
                "No se puede reasignar: " + conflicts.size() + " ocurrencia(s) solapan con asignaciones existentes.");
        this.conflicts = conflicts;
    }
}
