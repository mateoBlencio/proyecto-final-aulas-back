package ar.edu.utn.frc.classroom_allocation.allocation.model;

/**
 * Calidad de una asignación aula-evento.
 * Distinto de {@link AllocationStatus}: AllocationStatus describe el resultado global del solver
 * (éxito/parcial/infactible), AllocationQuality describe la calidad individual de cada asignación.
 */
public enum AllocationQuality {
    OPTIMAL,
    ACCEPTABLE,
    POOR,
    UNASSIGNED;

    /** Ratio mínimo de ocupación (alumnos/capacidad) para clasificar como OPTIMAL. */
    private static final double OPTIMAL_OCCUPANCY_THRESHOLD = 0.6;

    public static AllocationQuality of(boolean assigned, int overcrowding, double occupancyRatio) {
        if (!assigned) return UNASSIGNED;
        if (overcrowding > 0) return POOR;
        return occupancyRatio >= OPTIMAL_OCCUPANCY_THRESHOLD ? OPTIMAL : ACCEPTABLE;
    }
}
