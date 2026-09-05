package ar.edu.utn.frc.siga.optimizer.model;

import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
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
public class ClassAllocation {

    @PlanningId
    String id;

    OptimizerEvent event;

    @ValueRangeProvider
    List<OptimizerRoom> candidates;

    Set<String> conflictingEventIds;

    @PlanningPin
    boolean pinned;

    @Setter
    @PlanningVariable(allowsUnassigned = true)
    OptimizerRoom classroom;

    public ClassAllocation(OptimizerEvent event, List<OptimizerRoom> candidates, Set<String> conflictingEventIds) {
        this.id = event.planningId();
        this.event = event;
        this.candidates = candidates;
        this.conflictingEventIds = conflictingEventIds;
        this.pinned = false;
    }

    public static ClassAllocation pinned(OptimizerEvent event, OptimizerRoom classroom, Set<String> conflictingEventIds) {
        ClassAllocation allocation = new ClassAllocation(event, List.of(classroom), conflictingEventIds);
        allocation.pinned = true;
        allocation.classroom = classroom;
        return allocation;
    }

    public int getOvercrowding() {
        if (classroom == null) return event.enrolled();
        return classroom.overcrowding(event.enrolled());
    }

    public int getUnusedCapacity() {
        if (classroom == null) return 0;
        return classroom.undercrowding(event.enrolled());
    }

    public boolean conflictsWith(String eventId) {
        return conflictingEventIds.contains(eventId);
    }

    public String getCommissionKey() {
        return event.commissionKey();
    }

    public Long getBuildingId() {
        return classroom != null ? classroom.buildingId() : null;
    }
}
