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
                .filter(PermissionNotSatisfied::violates)
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint(NAME);
    }

    /**
     * Una asignación viola el permiso cuando tiene aula, no está fijada y el aula no habilita
     * la materia del evento. Las fijadas se excluyen igual que en {@code NoOverlap}: el solver no
     * las puede corregir, así que penalizarlas sólo vuelve el preview infeasible sin arreglo.
     */
    static boolean violates(ClassAllocation allocation) {
        return !allocation.isPinned()
                && allocation.getClassroom() != null
                && !allocation.getClassroom().permits(allocation.getEvent().subjectIds());
    }
}
