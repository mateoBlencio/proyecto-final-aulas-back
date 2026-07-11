package ar.edu.utn.frc.siga.solver.service.impl;

import ar.edu.utn.frc.siga.solver.model.ClassAllocation;
import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import org.jspecify.annotations.NonNull;

/**
 * Restricciones hard/soft de la asignación automática de aulas. La jerarquía de pesos
 * (ver ADR-008) es: sobrecupo (100.000 por alumno excedente) ≫ misma comisión en el mismo
 * edificio (4.000) > misma comisión en la misma aula (2.000) > capacidad ociosa (1).
 */
public class ClassroomConstraintProvider implements ConstraintProvider {

    private static final int OVERCROWDING_WEIGHT = 100_000;
    private static final int SAME_COMMISSION_DIFF_ROOM_WEIGHT = 2_000;
    private static final int SAME_COMMISSION_DIFF_BUILDING_WEIGHT = 4_000;

    /** Restricciones que evalúa el solver, en el orden en que se registran. */
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

    /**
     * Restricción dura: dos asignaciones no pueden compartir la misma aula si sus eventos
     * tienen horarios que se solapan (conflicto precalculado antes del solve). Se excluye el
     * par pinned-pinned porque esas ocupaciones ya son un hecho consumado, no algo a corregir.
     */
    Constraint noOverlap(ConstraintFactory factory) {
        return factory
                .forEachUniquePair(ClassAllocation.class, Joiners.equal(ClassAllocation::getClassroom),
                        Joiners.filtering((a, b) -> (!a.isPinned() || !b.isPinned())
                                        && a.conflictsWith(b.getEvent().planningId())))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Sin solapamiento");
    }

    /** Restricción blanda: penaliza (fuerte) asignar un evento a un aula con menos capacidad que sus inscriptos. */
    Constraint minimizeOvercrowding(ConstraintFactory factory) {
        return factory
                .forEach(ClassAllocation.class)
                .filter(a -> !a.isPinned() && a.getOvercrowding() > 0)
                .penalize(HardSoftScore.ONE_SOFT, a -> (long) a.getOvercrowding() * OVERCROWDING_WEIGHT)
                .asConstraint("Minimizar sobreocupacion");
    }

    /** Restricción blanda: penaliza la capacidad del aula que queda sin usar, para preferir aulas ajustadas al curso. */
    Constraint minimizeUnusedCapacity(ConstraintFactory factory) {
        return factory
                .forEach(ClassAllocation.class)
                .filter(a -> !a.isPinned())
                .penalize(HardSoftScore.ONE_SOFT,
                        ClassAllocation::getUnusedCapacity)
                .asConstraint("Minimizar subocupacion");
    }

    /**
     * Eventos de la misma comisión: preferir la misma aula (penaliza si difieren).
     * El pre-filtro (comisión no nula, no pinned, aula asignada) se aplica ANTES del join:
     * si se dejara post-join, el {@code equal(commissionKey)} indexaría todos los nulls en un
     * mismo bucket y emparejaría O(n²) eventos sin comisión → explosión de tuplas y OOM.
     */
    Constraint preferSameRoomSameCommission(ConstraintFactory factory) {
        return factory
                .forEach(ClassAllocation.class)
                .filter(a -> !a.isPinned() && a.getCommissionKey() != null && a.getClassroom() != null)
                .join(ClassAllocation.class,
                        Joiners.equal(ClassAllocation::getCommissionKey),
                        Joiners.lessThan(ClassAllocation::getId),
                        Joiners.filtering((a, b) -> !b.isPinned() && b.getClassroom() != null
                                && !a.getClassroom().equals(b.getClassroom())))
                .penalize(HardSoftScore.ONE_SOFT, (a, b) -> SAME_COMMISSION_DIFF_ROOM_WEIGHT)
                .asConstraint("Preferir misma aula por comision");
    }

    /**
     * Eventos de la misma comisión: en su defecto, preferir el mismo edificio.
     * Mismo pre-filtro antes del join que {@link #preferSameRoomSameCommission} para evitar
     * el emparejamiento cuadrático de eventos sin comisión.
     */
    Constraint preferSameBuildingSameCommission(ConstraintFactory factory) {
        return factory
                .forEach(ClassAllocation.class)
                .filter(a -> !a.isPinned() && a.getCommissionKey() != null && a.getBuildingId() != null)
                .join(ClassAllocation.class,
                        Joiners.equal(ClassAllocation::getCommissionKey),
                        Joiners.lessThan(ClassAllocation::getId),
                        Joiners.filtering((a, b) -> !b.isPinned() && b.getBuildingId() != null
                                && !a.getBuildingId().equals(b.getBuildingId())))
                .penalize(HardSoftScore.ONE_SOFT, (a, b) -> SAME_COMMISSION_DIFF_BUILDING_WEIGHT)
                .asConstraint("Preferir mismo edificio por comision");
    }
}
