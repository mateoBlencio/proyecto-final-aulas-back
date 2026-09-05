package ar.edu.utn.frc.siga.optimizer.service.impl;

import ar.edu.utn.frc.siga.optimizer.constraint.OptimizerConstraints;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import org.jspecify.annotations.NonNull;

public class ClassroomConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory factory) {
        return OptimizerConstraints.all().stream()
                .map(c -> c.define(factory))
                .toArray(Constraint[]::new);
    }
}
