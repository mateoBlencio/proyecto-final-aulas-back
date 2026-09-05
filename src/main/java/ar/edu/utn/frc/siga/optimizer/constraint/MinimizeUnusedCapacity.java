package ar.edu.utn.frc.siga.optimizer.constraint;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ar.edu.utn.frc.siga.optimizer.model.ClassAllocation;
import ar.edu.utn.frc.siga.settings.model.SettingKey;

import java.util.Optional;

public class MinimizeUnusedCapacity implements OptimizerConstraint {

    static final String NAME = "Minimizar subocupacion";

    public MinimizeUnusedCapacity() {
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Constraint define(ConstraintFactory factory) {
        return factory
                .forEach(ClassAllocation.class)
                .filter(a -> !a.isPinned())
                .penalize(HardMediumSoftScore.ONE_SOFT, a -> (long) a.getUnusedCapacity())
                .asConstraint(NAME);
    }

    @Override
    public Optional<SettingKey> weightKey() {
        return Optional.of(SettingKey.OPTIMIZER_WEIGHT_UNUSED_CAPACITY);
    }
}
