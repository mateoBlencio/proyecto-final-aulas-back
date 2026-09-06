package ar.edu.utn.frc.siga.optimizer.constraint;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ar.edu.utn.frc.siga.optimizer.model.ClassAllocation;

public class PermissionNotSatisfied implements OptimizerConstraint {

    static final String NAME = "Permiso no satisfecho";

    public PermissionNotSatisfied() {
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Constraint define(ConstraintFactory factory) {
        return factory
                .forEach(ClassAllocation.class)
                .filter(a -> a.getClassroom() != null
                        && !a.getClassroom().permits(a.getEvent().subjectIds()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(NAME);
    }
}
