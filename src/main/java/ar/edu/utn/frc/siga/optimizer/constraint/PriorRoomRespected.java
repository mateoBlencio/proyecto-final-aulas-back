package ar.edu.utn.frc.siga.optimizer.constraint;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.Joiners;
import ar.edu.utn.frc.siga.optimizer.model.ClassAllocation;

public class PriorRoomRespected implements OptimizerConstraint {

    static final String NAME = "Aula previa respetada";

    public PriorRoomRespected() {
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Constraint define(ConstraintFactory factory) {
        return factory
                .forEachIncludingUnassigned(ClassAllocation.class)
                .filter(a -> !a.isPinned() && a.getClassroom() == null && a.getPriorRoomId() != null)
                .join(ClassAllocation.class,
                        Joiners.equal(ClassAllocation::getPriorRoomId, ClassAllocation::getClassroomId),
                        Joiners.filtering((owner, other) -> owner.conflictsWith(other.getEvent().planningId())))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(NAME);
    }
}
