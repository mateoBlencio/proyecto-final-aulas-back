package ar.edu.utn.frc.siga.allocation.exception;

import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceConflictDto;
import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.modulith.NamedInterface;

import java.io.Serial;
import java.util.List;
import java.util.Map;

@Getter
@NamedInterface("api")
public final class OverlapObservationRequiredException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<OccurrenceConflictDto> overlaps;

    public OverlapObservationRequiredException(List<OccurrenceConflictDto> overlaps, Map<Long, Integer> roomNumbers) {
        super(HttpStatus.BAD_REQUEST, "Observation required",
                ConflictDetail.describe(overlaps, roomNumbers)
                        + " Está dentro del margen permitido: indicá un motivo para confirmar.");
        this.overlaps = overlaps;
        withProperty("overlaps", overlaps);
        withProperty("classroomIds", ConflictDetail.classroomIds(overlaps));
    }
}
