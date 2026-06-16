package ar.edu.utn.frc.classroom_allocation.solver.optimization.impl;

import ar.edu.utn.frc.classroom_allocation.space.model.Classroom;
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
    List<Classroom> classrooms;

    @PlanningEntityCollectionProperty
    List<ClassAssignment> assignments;

    @Setter
    @PlanningScore
    HardSoftScore score;
}
