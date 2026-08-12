package ar.edu.utn.frc.siga.optimizer.model;

import org.springframework.modulith.NamedInterface;

/**
 * Aula candidata para el solver: capacidad y edificio, sin más datos de dominio.
 * Es el rango de valores ({@code @ValueRangeProvider}) de la variable de planificación
 * {@code classroom} de {@link ClassAllocation}.
 */
@NamedInterface("api")
public record OptimizerRoom(Integer id, Integer capacity, Integer buildingId) {
    /** Alumnos que exceden la capacidad del aula (0 si entran todos). */
    public int overcrowding(int enrolled) {
        return Math.max(0, enrolled - capacity);
    }

    /** Capacidad que queda sin usar si se asigna este evento (0 si sobrepasa la capacidad). */
    public int undercrowding(int enrolled) {
        return Math.max(0, capacity - enrolled);
    }
}
