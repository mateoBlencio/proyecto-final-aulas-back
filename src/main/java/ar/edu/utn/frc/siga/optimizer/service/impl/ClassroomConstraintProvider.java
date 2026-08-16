package ar.edu.utn.frc.siga.optimizer.service.impl;

import ar.edu.utn.frc.siga.optimizer.model.ClassAllocation;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import lombok.Setter;
import org.jspecify.annotations.NonNull;

@Setter
public class ClassroomConstraintProvider implements ConstraintProvider {

    private int overcrowdingWeight = 100_000;
    private int sameCommissionDiffRoomWeight = 2_000;
    private int sameCommissionDiffBuildingWeight = 4_000;
    private int unusedCapacityWeight = 1;

    @Override
    public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory factory) {
        return new Constraint[]{
                noOverlap(factory),
                allocateAllPossible(factory),
                minimizeOvercrowding(factory),
                minimizeUnusedCapacity(factory),
                preferSameRoomSameCommission(factory),
                preferSameBuildingSameCommission(factory)
        };
    }

    Constraint noOverlap(ConstraintFactory factory) {
        return factory
                .forEachUniquePair(ClassAllocation.class, Joiners.equal(ClassAllocation::getClassroom),
                        Joiners.filtering((a, b) -> (!a.isPinned() || !b.isPinned())
                                        && a.conflictsWith(b.getEvent().planningId())))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Sin solapamiento");
    }

    Constraint allocateAllPossible(ConstraintFactory factory) {
        return factory
                .forEachIncludingUnassigned(ClassAllocation.class)
                .filter(a -> !a.isPinned() && a.getClassroom() == null)
                .penalize(HardMediumSoftScore.ONE_MEDIUM)
                .asConstraint("Asignar todo lo posible");
    }

    Constraint minimizeOvercrowding(ConstraintFactory factory) {
        return factory
                .forEach(ClassAllocation.class)
                .filter(a -> !a.isPinned() && a.getOvercrowding() > 0)
                .penalize(HardMediumSoftScore.ONE_SOFT, a -> (long) a.getOvercrowding() * overcrowdingWeight)
                .asConstraint("Minimizar sobreocupacion");
    }

    Constraint minimizeUnusedCapacity(ConstraintFactory factory) {
        return factory
                .forEach(ClassAllocation.class)
                .filter(a -> !a.isPinned())
                .penalize(HardMediumSoftScore.ONE_SOFT,
                        a -> (long) a.getUnusedCapacity() * unusedCapacityWeight)
                .asConstraint("Minimizar subocupacion");
    }

    Constraint preferSameRoomSameCommission(ConstraintFactory factory) {
        return factory
                .forEach(ClassAllocation.class)
                .filter(a -> !a.isPinned() && a.getCommissionKey() != null && a.getClassroom() != null)
                .join(ClassAllocation.class,
                        Joiners.equal(ClassAllocation::getCommissionKey),
                        Joiners.lessThan(ClassAllocation::getId),
                        Joiners.filtering((a, b) -> !b.isPinned() && b.getClassroom() != null
                                && !a.getClassroom().equals(b.getClassroom())))
                .penalize(HardMediumSoftScore.ONE_SOFT, (a, b) -> sameCommissionDiffRoomWeight)
                .asConstraint("Preferir misma aula por comision");
    }

    Constraint preferSameBuildingSameCommission(ConstraintFactory factory) {
        return factory
                .forEach(ClassAllocation.class)
                .filter(a -> !a.isPinned() && a.getCommissionKey() != null && a.getBuildingId() != null)
                .join(ClassAllocation.class,
                        Joiners.equal(ClassAllocation::getCommissionKey),
                        Joiners.lessThan(ClassAllocation::getId),
                        Joiners.filtering((a, b) -> !b.isPinned() && b.getBuildingId() != null
                                && !a.getBuildingId().equals(b.getBuildingId())))
                .penalize(HardMediumSoftScore.ONE_SOFT, (a, b) -> sameCommissionDiffBuildingWeight)
                .asConstraint("Preferir mismo edificio por comision");
    }
}
