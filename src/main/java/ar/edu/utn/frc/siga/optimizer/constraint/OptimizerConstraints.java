package ar.edu.utn.frc.siga.optimizer.constraint;

import java.util.List;
import java.util.ServiceLoader;

public final class OptimizerConstraints {
    private static final List<OptimizerConstraint> ALL =
            ServiceLoader.load(OptimizerConstraint.class).stream()
                    .map(ServiceLoader.Provider::get).toList();

    public static List<OptimizerConstraint> all() {
        return ALL;
    }

    private OptimizerConstraints() {}
}
