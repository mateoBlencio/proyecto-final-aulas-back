package ar.edu.utn.frc.siga.allocation.validator;

import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceConflictDto;
import org.springframework.modulith.NamedInterface;

import java.util.List;

@NamedInterface("api")
public record OverlapReview(List<OccurrenceConflictDto> blocking, List<OccurrenceConflictDto> tolerated) {

    public boolean isClean() {
        return blocking.isEmpty() && tolerated.isEmpty();
    }
}
