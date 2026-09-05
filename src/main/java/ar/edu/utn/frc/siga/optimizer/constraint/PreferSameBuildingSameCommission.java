package ar.edu.utn.frc.siga.optimizer.constraint;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.Joiners;
import ar.edu.utn.frc.siga.optimizer.model.ClassAllocation;
import ar.edu.utn.frc.siga.settings.model.SettingKey;

import java.util.Optional;

public class PreferSameBuildingSameCommission implements OptimizerConstraint {

    static final String NAME = "Preferir mismo edificio por comision";

    public PreferSameBuildingSameCommission() {
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Constraint define(ConstraintFactory factory) {
        return factory
                .forEach(ClassAllocation.class)
                .filter(a -> !a.isPinned() && a.getCommissionKey() != null && a.getBuildingId() != null)
                .join(ClassAllocation.class,
                        Joiners.equal(ClassAllocation::getCommissionKey),
                        Joiners.lessThan(ClassAllocation::getId),
                        Joiners.filtering((a, b) -> !b.isPinned() && b.getBuildingId() != null
                                && !a.getBuildingId().equals(b.getBuildingId())))
                .penalize(HardMediumSoftScore.ONE_SOFT)
                .asConstraint(NAME);
    }

    @Override
    public Optional<SettingKey> weightKey() {
        return Optional.of(SettingKey.OPTIMIZER_WEIGHT_SAME_COMMISSION_DIFF_BUILDING);
    }
}
