package ar.edu.utn.frc.siga.solver.optimization;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;

public class ClassroomConstraintProvider implements ConstraintProvider {

    private static final int OVERCROWDING_WEIGHT = 100_000;

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
                noOverlap(factory),
                minimizeOvercrowding(factory),
                minimizeUnusedCapacity(factory)
        };
    }

    Constraint noOverlap(ConstraintFactory factory) {
        return factory
                .forEachUniquePair(ClassAssignment.class,
                        Joiners.equal(ClassAssignment::getClassroom),
                        Joiners.filtering((a, b) ->
                                a.conflictsWith(b.getEvent().planningId())))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Sin solapamiento");
    }

    Constraint minimizeOvercrowding(ConstraintFactory factory) {
        return factory
                .forEach(ClassAssignment.class)
                .filter(a -> a.getOvercrowding() > 0)
                .penalize(HardSoftScore.ONE_SOFT,
                        a -> (long) a.getOvercrowding() * OVERCROWDING_WEIGHT)
                .asConstraint("Minimizar sobreocupacion");
    }

    Constraint minimizeUnusedCapacity(ConstraintFactory factory) {
        return factory
                .forEach(ClassAssignment.class)
                .penalize(HardSoftScore.ONE_SOFT,
                        ClassAssignment::getUnusedCapacity)
                .asConstraint("Minimizar capacidad sin usar");
    }
}
