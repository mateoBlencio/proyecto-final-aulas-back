package ar.edu.utn.frc.siga.solver.model;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record SolverRoom(Integer id, Integer capacity, Integer buildingId) {
    public int overcrowding(int enrolled) {
        return Math.max(0, enrolled - capacity);
    }

    public int undercrowding(int enrolled) {
        return Math.max(0, capacity - enrolled);
    }
}
