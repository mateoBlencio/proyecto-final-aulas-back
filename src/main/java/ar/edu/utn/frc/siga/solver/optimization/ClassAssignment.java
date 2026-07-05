package ar.edu.utn.frc.siga.solver.optimization;

import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import ar.edu.utn.frc.siga.solver.model.SolverEvent;
import ar.edu.utn.frc.siga.solver.model.SolverRoom;
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

    SolverEvent event;

    /**
     * Value range por entidad: las aulas que este evento puede recibir
     * (todas, o solo la fijada si viene pinneado).
     */
    @ValueRangeProvider
    List<SolverRoom> candidates;

    Set<String> conflictingEventIds;

    @Setter
    @PlanningVariable
    SolverRoom classroom;

    public ClassAssignment(SolverEvent event, List<SolverRoom> candidates, Set<String> conflictingEventIds) {
        this.id = event.planningId();
        this.event = event;
        this.candidates = candidates;
        this.conflictingEventIds = conflictingEventIds;
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
}
