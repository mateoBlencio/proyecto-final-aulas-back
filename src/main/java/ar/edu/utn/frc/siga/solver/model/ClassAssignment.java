package ar.edu.utn.frc.siga.solver.model;

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

    /**
     * Asignación existente inmovible: representa ocupación previa que el solver no
     * puede cambiar; solo participa del no-solapamiento para bloquear a los nuevos.
     */
    @PlanningPin
    boolean pinned;

    @Setter
    @PlanningVariable
    SolverRoom classroom;

    public ClassAssignment(SolverEvent event, List<SolverRoom> candidates, Set<String> conflictingEventIds) {
        this.id = event.planningId();
        this.event = event;
        this.candidates = candidates;
        this.conflictingEventIds = conflictingEventIds;
        this.pinned = false;
    }

    /** Ocupación existente: aula fija, no planificable. */
    public static ClassAssignment pinned(SolverEvent event, SolverRoom classroom, Set<String> conflictingEventIds) {
        ClassAssignment assignment = new ClassAssignment(event, List.of(classroom), conflictingEventIds);
        assignment.pinned = true;
        assignment.classroom = classroom;
        return assignment;
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

    /** Comisión del evento, para la preferencia soft de agrupamiento. */
    public String getCommissionKey() {
        return event.commissionKey();
    }

    /** Edificio del aula asignada (null si sin asignar). */
    public Integer getBuildingId() {
        return classroom != null ? classroom.buildingId() : null;
    }
}
