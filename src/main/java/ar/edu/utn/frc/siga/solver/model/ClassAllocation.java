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

/**
 * Entidad de planificación del solver: la asignación aula↔evento que Timefold ajusta
 * durante el solve. Cada instancia representa un evento (nuevo o una ocupación existente
 * pinned) y su variable de planificación es el aula elegida.
 */
@Getter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@PlanningEntity
public class ClassAllocation {

    /** Identificador de planificación: el {@code planningId} del evento. */
    @PlanningId
    String id;

    /** Evento del solver que esta entidad representa. */
    SolverEvent event;

    /**
     * Value range por entidad: las aulas que este evento puede recibir
     * (todas, o solo la fijada si viene pinneado).
     */
    @ValueRangeProvider
    List<SolverRoom> candidates;

    /** IDs de eventos cuyo horario se solapa con este (precalculado antes del solve). */
    Set<String> conflictingEventIds;

    /**
     * Asignación existente inmovible: representa ocupación previa que el solver no
     * puede cambiar; solo participa del no-solapamiento para bloquear a los nuevos.
     */
    @PlanningPin
    boolean pinned;

    /** Variable de planificación: aula asignada a este evento (null hasta que el solver decide). */
    @Setter
    @PlanningVariable
    SolverRoom classroom;

    public ClassAllocation(SolverEvent event, List<SolverRoom> candidates, Set<String> conflictingEventIds) {
        this.id = event.planningId();
        this.event = event;
        this.candidates = candidates;
        this.conflictingEventIds = conflictingEventIds;
        this.pinned = false;
    }

    /** Ocupación existente: aula fija, no planificable. */
    public static ClassAllocation pinned(SolverEvent event, SolverRoom classroom, Set<String> conflictingEventIds) {
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

    /** Comisión del evento, para la preferencia soft de agrupamiento. */
    public String getCommissionKey() {
        return event.commissionKey();
    }

    /** Edificio del aula asignada (null si sin asignar). */
    public Integer getBuildingId() {
        return classroom != null ? classroom.buildingId() : null;
    }
}
