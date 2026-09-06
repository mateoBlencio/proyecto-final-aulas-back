package ar.edu.utn.frc.siga.optimizer.model;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record OptimizerRoom(Long id, Integer capacity, Long buildingId,
                            boolean openToAll, Set<Long> allowedSubjectIds) {

    public OptimizerRoom(Long id, Integer capacity, Long buildingId) {
        this(id, capacity, buildingId, true, Set.of());
    }

    public int overcrowding(int enrolled) {
        return Math.max(0, enrolled - capacity);
    }

    public int undercrowding(int enrolled) {
        return Math.max(0, capacity - enrolled);
    }

    public boolean permits(Collection<Long> subjectIds) {
        return openToAll || (subjectIds != null && !Collections.disjoint(allowedSubjectIds, subjectIds));
    }
}
