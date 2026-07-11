package ar.edu.utn.frc.siga.solver.model;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.score.HardSoftScore;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Problema/solución de planificación que arma y resuelve el solver: un corte de eventos
 * (nuevos + ocupación existente pinned) a repartir entre las aulas disponibles. Timefold
 * completa {@code allocations} y {@code score} durante el solve.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@PlanningSolution
public class ScheduleSolution {

    /** Hecho del problema: aulas disponibles, no las modifica el solver. */
    @ProblemFactCollectionProperty
    List<SolverRoom> classrooms;

    /** Entidades de planificación: una por evento (nuevo o pinned) a resolver. */
    @PlanningEntityCollectionProperty
    List<ClassAllocation> allocations;

    /** Puntaje hard/soft de la solución actual, calculado por el {@code ConstraintProvider}. */
    @Setter
    @PlanningScore
    HardSoftScore score;

    public ScheduleSolution(List<SolverRoom> classrooms, List<ClassAllocation> allocations) {
        this.classrooms = classrooms;
        this.allocations = allocations;
    }
}
