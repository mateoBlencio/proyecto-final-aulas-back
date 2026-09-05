package ar.edu.utn.frc.siga.optimizer.constraint;

import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ar.edu.utn.frc.siga.settings.model.SettingKey;

import java.util.Optional;

public interface OptimizerConstraint {
    String name();
    Constraint define(ConstraintFactory factory);
    default Optional<SettingKey> weightKey() {
        return Optional.empty();
    }
}
