package ar.edu.utn.frc.siga.optimizer.constraint;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.Joiners;
import ar.edu.utn.frc.siga.optimizer.model.ClassAllocation;

public class NoOverlap implements OptimizerConstraint {

    static final String NAME = "Sin solapamiento";

    public NoOverlap() {
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Constraint define(ConstraintFactory factory) {
        return factory
                .forEachUniquePair(ClassAllocation.class, Joiners.equal(ClassAllocation::getClassroom),
                        Joiners.filtering((a, b) -> (!a.isPinned() || !b.isPinned())
                                        && a.conflictsWith(b.getEvent().planningId())))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(NAME);
    }
}
