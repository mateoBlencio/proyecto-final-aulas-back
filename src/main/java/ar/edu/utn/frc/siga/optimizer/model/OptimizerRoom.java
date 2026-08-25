package ar.edu.utn.frc.siga.optimizer.model;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record OptimizerRoom(Long id, Integer capacity, Long buildingId) {
    public int overcrowding(int enrolled) {
        return Math.max(0, enrolled - capacity);
    }

    public int undercrowding(int enrolled) {
        return Math.max(0, capacity - enrolled);
    }
}
