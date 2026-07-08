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

@Getter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@PlanningSolution
public class ScheduleSolution {

    @ProblemFactCollectionProperty
    List<SolverRoom> classrooms;

    @PlanningEntityCollectionProperty
    List<ClassAssignment> assignments;

    @Setter
    @PlanningScore
    HardSoftScore score;

    public ScheduleSolution(List<SolverRoom> classrooms, List<ClassAssignment> assignments) {
        this.classrooms = classrooms;
        this.assignments = assignments;
    }
}
