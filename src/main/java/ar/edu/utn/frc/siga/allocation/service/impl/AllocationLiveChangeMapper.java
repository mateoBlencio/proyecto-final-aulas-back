package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.common.realtime.LiveChange;
import ar.edu.utn.frc.siga.common.realtime.LiveChangeMapper;
import ar.edu.utn.frc.siga.common.realtime.LiveTopic;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/** Una asignación afecta al edificio del aula que tiene y, si cambió de aula, también al de la anterior. */
@Component
class AllocationLiveChangeMapper implements LiveChangeMapper<Allocation> {

    @Override
    public Class<Allocation> entityType() {
        return Allocation.class;
    }

    @Override
    public LiveChange map(Allocation current, PreviousState previous) {
        if (previous == null) {
            return LiveChange.unscoped(LiveTopic.ALLOCATIONS);
        }
        Set<Long> classroomIds = new HashSet<>();
        if (current.getClassroomId() != null) classroomIds.add(current.getClassroomId());
        if (previous.get("classroomId") instanceof Long before) classroomIds.add(before);
        return LiveChange.inClassrooms(LiveTopic.ALLOCATIONS, classroomIds);
    }
}
