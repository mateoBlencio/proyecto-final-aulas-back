package ar.edu.utn.frc.siga.solver.optimization;

public record SolverRoom(Integer id, Integer capacity) {
    public int overcrowding(int enrolled) {
        return Math.max(0, enrolled - capacity);
    }

    public int undercrowding(int enrolled) {
        return Math.max(0, capacity - enrolled);
    }
}
