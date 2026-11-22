package ar.edu.utn.frc.siga.optimizer.constraint;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ar.edu.utn.frc.siga.optimizer.model.ClassAllocation;
import ar.edu.utn.frc.siga.settings.model.SettingKey;

import java.util.Optional;

public class PreferRoomWithComputers implements OptimizerConstraint {

    static final String NAME = "Preferir aula con computadoras";

    public PreferRoomWithComputers() {
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Constraint define(ConstraintFactory factory) {
        return factory
                .forEach(ClassAllocation.class)
                .filter(a -> !a.isPinned() && a.getMissingComputers() > 0)
                .penalize(HardMediumSoftScore.ONE_SOFT, ClassAllocation::getMissingComputers)
                .asConstraint(NAME);
    }

    @Override
    public Optional<SettingKey> weightKey() {
        return Optional.of(SettingKey.OPTIMIZER_WEIGHT_MISSING_COMPUTER);
    }
}
