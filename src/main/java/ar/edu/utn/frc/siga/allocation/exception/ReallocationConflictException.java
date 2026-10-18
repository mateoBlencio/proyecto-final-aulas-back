package ar.edu.utn.frc.siga.allocation.exception;

import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceConflictDto;
import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.io.Serial;
import java.util.List;
import java.util.Map;

@Getter
public final class ReallocationConflictException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<OccurrenceConflictDto> conflicts;

    public ReallocationConflictException(List<OccurrenceConflictDto> conflicts, Map<Long, Integer> roomNumbers) {
        super(HttpStatus.CONFLICT, "Reallocation conflict", ConflictDetail.describe(conflicts, roomNumbers));
        this.conflicts = conflicts;
        withProperty("code", "ALLOCATION_CONFLICT");
        withProperty("conflicts", conflicts);
        withProperty("classroomIds", ConflictDetail.classroomIds(conflicts));
    }
}
