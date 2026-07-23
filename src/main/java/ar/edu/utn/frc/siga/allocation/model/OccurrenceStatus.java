package ar.edu.utn.frc.siga.allocation.model;

/**
 * Máquina de estados de una {@link Occurrence}. Las transiciones están centralizadas en
 * los servicios de asignación: pasar a {@code ASSIGNED} ocurre al asignar/reasignar aula.
 * No hay, hoy, ningún camino de vuelta a {@code SCHEDULED}.
 */
public enum OccurrenceStatus {
    /** Programada, sin aula asignada. Estado inicial. */
    SCHEDULED,
    /** Tiene aula: existe una {@link Allocation} vigente para esta occurrence. */
    ASSIGNED,
    /** Cancelada. */
    CANCELLED,
    /** Suspendida intencionalmente: liberó su aula pero, a diferencia de {@code SCHEDULED}, no queda pendiente de asignación. */
    SUSPENDED
}