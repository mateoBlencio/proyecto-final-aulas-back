package ar.edu.utn.frc.siga.solver.service.impl;

import ar.edu.utn.frc.siga.solver.model.ClassAllocation;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import lombok.Setter;
import org.jspecify.annotations.NonNull;

/**
 * Restricciones de la asignación automática de aulas, en tres niveles (ver ADR-008):
 * HARD (no-solape) ≫ MEDIUM (asignar todo lo posible) ≫ SOFT. La jerarquía de pesos SOFT por
 * defecto es: sobrecupo (100.000 por alumno excedente) ≫ misma comisión en el mismo edificio
 * (4.000) > misma comisión en la misma aula (2.000) > capacidad ociosa (1). El nivel MEDIUM
 * empuja a asignar aula a cada evento que no rompa el HARD, aun sobrecupando (soft), y sólo
 * deja sin aula lo verdaderamente inubicable (solape en todas las candidatas).
 * <p>
 * Los pesos SOFT son configurables vía {@code siga.solver.weights.*} en application.yaml
 * (ver {@link ar.edu.utn.frc.siga.solver.config.SolverProperties}): Timefold instancia esta
 * clase con su constructor sin argumentos y aplica esos valores por setter
 * ({@code constraintProviderCustomProperties}) antes de llamar a {@link #defineConstraints}.
 */
@Setter
public class ClassroomConstraintProvider implements ConstraintProvider {

    private int overcrowdingWeight = 100_000;
    private int sameCommissionDiffRoomWeight = 2_000;
    private int sameCommissionDiffBuildingWeight = 4_000;

    /** Restricciones que evalúa el solver, en el orden en que se registran. */
    @Override
    public @NonNull Constraint[] defineConstraints(@NonNull ConstraintFactory factory) {
        return new Constraint[]{
                noOverlap(factory),
                assignAllPossible(factory),
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
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Sin solapamiento");
    }

    /**
     * Restricción media: penaliza cada evento no-pinned que quede sin aula. Domina a todo el
     * nivel SOFT, así que el solver asigna aula siempre que no rompa el HARD (aun sobrecupando,
     * que es soft); sólo deja sin aula lo verdaderamente inubicable (solape en toda candidata).
     * Se usa la variante que incluye entidades sin asignar; {@code forEach} las excluiría.
     */
    Constraint assignAllPossible(ConstraintFactory factory) {
        return factory
                .forEachIncludingUnassigned(ClassAllocation.class)
                .filter(a -> !a.isPinned() && a.getClassroom() == null)
                .penalize(HardMediumSoftScore.ONE_MEDIUM)
                .asConstraint("Asignar todo lo posible");
    }

    /** Restricción blanda: penaliza (fuerte) asignar un evento a un aula con menos capacidad que sus inscriptos. */
    Constraint minimizeOvercrowding(ConstraintFactory factory) {
        return factory
                .forEach(ClassAllocation.class)
                .filter(a -> !a.isPinned() && a.getOvercrowding() > 0)
                .penalize(HardMediumSoftScore.ONE_SOFT, a -> (long) a.getOvercrowding() * overcrowdingWeight)
                .asConstraint("Minimizar sobreocupacion");
    }

    /** Restricción blanda: penaliza la capacidad del aula que queda sin usar, para preferir aulas ajustadas al curso. */
    Constraint minimizeUnusedCapacity(ConstraintFactory factory) {
        return factory
                .forEach(ClassAllocation.class)
                .filter(a -> !a.isPinned())
                .penalize(HardMediumSoftScore.ONE_SOFT,
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
                .penalize(HardMediumSoftScore.ONE_SOFT, (a, b) -> sameCommissionDiffRoomWeight)
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
                .penalize(HardMediumSoftScore.ONE_SOFT, (a, b) -> sameCommissionDiffBuildingWeight)
                .asConstraint("Preferir mismo edificio por comision");
    }
}
