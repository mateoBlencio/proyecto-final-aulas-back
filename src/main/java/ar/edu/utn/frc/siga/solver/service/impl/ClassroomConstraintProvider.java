package ar.edu.utn.frc.siga.solver.service.impl;

import ar.edu.utn.frc.siga.solver.model.ClassAllocation;
import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import org.jspecify.annotations.NonNull;

public class ClassroomConstraintProvider implements ConstraintProvider {

    private static final int OVERCROWDING_WEIGHT = 100_000;
    private static final int SAME_COMMISSION_DIFF_ROOM_WEIGHT = 2_000;
    private static final int SAME_COMMISSION_DIFF_BUILDING_WEIGHT = 4_000;

    @Override
    public Constraint[] defineConstraints(@NonNull ConstraintFactory factory) {
        return new Constraint[]{
                noOverlap(factory),
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
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Sin solapamiento");
    }

    Constraint minimizeOvercrowding(ConstraintFactory factory) {
        return factory
                .forEach(ClassAllocation.class)
                .filter(a -> !a.isPinned() && a.getOvercrowding() > 0)
                .penalize(HardSoftScore.ONE_SOFT, a -> (long) a.getOvercrowding() * OVERCROWDING_WEIGHT)
                .asConstraint("Minimizar sobreocupacion");
    }

    Constraint minimizeUnusedCapacity(ConstraintFactory factory) {
        return factory
                .forEach(ClassAllocation.class)
                .filter(a -> !a.isPinned())
                .penalize(HardSoftScore.ONE_SOFT,
                        ClassAllocation::getUnusedCapacity)
                .asConstraint("Minimizar subocupacion");
    }

    /** Eventos de la misma comisión: preferir la misma aula (penaliza si difieren). */
    Constraint preferSameRoomSameCommission(ConstraintFactory factory) {
        return factory
                .forEachUniquePair(ClassAllocation.class,
                        Joiners.equal(ClassAllocation::getCommissionKey))
                .filter((a, b) -> !a.isPinned() && !b.isPinned()
                        && a.getCommissionKey() != null
                        && a.getClassroom() != null && b.getClassroom() != null
                        && !a.getClassroom().equals(b.getClassroom()))
                .penalize(HardSoftScore.ONE_SOFT, (a, b) -> SAME_COMMISSION_DIFF_ROOM_WEIGHT)
                .asConstraint("Preferir misma aula por comision");
    }

    /** Eventos de la misma comisión: en su defecto, preferir el mismo edificio. */
    Constraint preferSameBuildingSameCommission(ConstraintFactory factory) {
        return factory
                .forEachUniquePair(ClassAllocation.class,
                        Joiners.equal(ClassAllocation::getCommissionKey))
                .filter((a, b) -> !a.isPinned() && !b.isPinned()
                        && a.getCommissionKey() != null
                        && a.getBuildingId() != null && b.getBuildingId() != null
                        && !a.getBuildingId().equals(b.getBuildingId()))
                .penalize(HardSoftScore.ONE_SOFT, (a, b) -> SAME_COMMISSION_DIFF_BUILDING_WEIGHT)
                .asConstraint("Preferir mismo edificio por comision");
    }
}
