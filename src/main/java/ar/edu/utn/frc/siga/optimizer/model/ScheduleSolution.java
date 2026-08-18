package ar.edu.utn.frc.siga.optimizer.model;

import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;
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
    List<OptimizerRoom> classrooms;

    @PlanningEntityCollectionProperty
    List<ClassAllocation> allocations;

    @Setter
    ConstraintWeightOverrides<HardMediumSoftScore> constraintWeightOverrides;

    @Setter
    @PlanningScore
    HardMediumSoftScore score;

    public ScheduleSolution(List<OptimizerRoom> classrooms, List<ClassAllocation> allocations,
                            ConstraintWeightOverrides<HardMediumSoftScore> constraintWeightOverrides) {
        this.classrooms = classrooms;
        this.allocations = allocations;
        this.constraintWeightOverrides = constraintWeightOverrides;
    }
}
