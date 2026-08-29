package ar.edu.utn.frc.siga.optimizer.constraint;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ar.edu.utn.frc.siga.optimizer.model.ClassAllocation;

public class AllocateAllPossible implements OptimizerConstraint {

    static final String NAME = "Asignar todo lo posible";

    public AllocateAllPossible() {
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Constraint define(ConstraintFactory factory) {
        return factory
                .forEachIncludingUnassigned(ClassAllocation.class)
                .filter(a -> !a.isPinned() && a.getClassroom() == null)
                .penalize(HardMediumSoftScore.ONE_MEDIUM)
                .asConstraint(NAME);
    }
}
