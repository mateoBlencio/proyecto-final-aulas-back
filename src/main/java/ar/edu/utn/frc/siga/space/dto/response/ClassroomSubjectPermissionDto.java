package ar.edu.utn.frc.siga.space.dto.response;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record ClassroomSubjectPermissionDto(Long classroomId, boolean openToAll, Set<Long> allowedSubjectIds) {

    public boolean permits(Long subjectId) {
        return openToAll || (subjectId != null && allowedSubjectIds.contains(subjectId));
    }

    public boolean permitsAny(Collection<Long> subjectIds) {
        return openToAll || (subjectIds != null && !Collections.disjoint(allowedSubjectIds, subjectIds));
    }
}
