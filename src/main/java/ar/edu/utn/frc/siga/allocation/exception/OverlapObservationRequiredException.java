package ar.edu.utn.frc.siga.allocation.exception;

import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceConflictDto;
import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.modulith.NamedInterface;

import java.io.Serial;
import java.util.List;

@Getter
@NamedInterface("api")
public final class OverlapObservationRequiredException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<OccurrenceConflictDto> overlaps;

    public OverlapObservationRequiredException(List<OccurrenceConflictDto> overlaps) {
        super(HttpStatus.BAD_REQUEST, "Observation required",
                "Esta asignación genera " + overlaps.size() + " superposición(es) dentro del margen "
                        + "permitido. Indicá una observación explicando el motivo.");
        this.overlaps = overlaps;
        withProperty("overlaps", overlaps);
    }
}
