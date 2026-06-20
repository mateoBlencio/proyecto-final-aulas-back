package ar.edu.utn.frc.classroom_allocation.solver.optimization.impl;

import ar.edu.utn.frc.classroom_allocation.space.model.Classroom;
import ar.edu.utn.frc.classroom_allocation.allocation.model.AcademicEvent;
import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Set;

@Getter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@PlanningEntity
public class ClassAssignment {

    @PlanningId
    String id;

    AcademicEvent event;
    List<Classroom> candidates;
    Set<String> conflictingEventIds;

    @Setter
    @PlanningVariable
    Classroom classroom;

    public ClassAssignment(AcademicEvent event, List<Classroom> candidates, Set<String> conflictingEventIds) {
        this.id = event.getPlanningId();
        this.event = event;
        this.candidates = candidates;
        this.conflictingEventIds = conflictingEventIds;
    }

    @ValueRangeProvider
    public List<Classroom> getCandidates() {
        return candidates;
    }

    public int getOvercrowding() {
        if (classroom == null) return event.getEnrolled();
        return classroom.overcrowding(event.getEnrolled());
    }

    public int getUnusedCapacity() {
        if (classroom == null) return 0;
        return classroom.undercrowding(event.getEnrolled());
    }

    public boolean conflictsWith(String eventId) {
        return conflictingEventIds.contains(eventId);
    }
}
