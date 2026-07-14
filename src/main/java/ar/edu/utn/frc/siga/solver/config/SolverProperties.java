package ar.edu.utn.frc.siga.solver.config;

import ai.timefold.solver.core.config.solver.EnvironmentMode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Propiedades de configuración del solver, bajo el prefijo {@code siga.solver} de application.yaml. */
@Getter
@Setter
@ConfigurationProperties(prefix = "siga.solver")
public class SolverProperties {

    /**
     * Cantidad de solves concurrentes que acepta el SolverManager; los excedentes
     * se encolan. "AUTO" deja que Timefold decida según los cores disponibles.
     */
    private String parallelSolverCount = "AUTO";

    /**
     * Terminación temprana: segundos sin mejora del mejor score antes de cortar.
     * Complementa el límite total del request. 0 la deshabilita.
     */
    private long unimprovedSecondsLimit = 10;

    /**
     * PHASE_ASSERT (default de Timefold): reproducible, asserts baratos al final de
     * cada fase. NO_ASSERT si hace falta exprimir performance; FULL_ASSERT/
     * TRACKED_FULL_ASSERT para debugging de score corruption (mucho más lento).
     */
    private EnvironmentMode environmentMode = EnvironmentMode.PHASE_ASSERT;

    private Preview preview = new Preview();

    private Weights weights = new Weights();

    /**
     * Preview generada y guardada para confirmarla después. El TTL acota la
     * obsolescencia (bound de staleness), no es eviction por memoria.
     */
    @Getter
    @Setter
    public static class Preview {
        private long ttlMinutes = 30;
    }

    /**
     * Pesos SOFT del {@code ClassroomConstraintProvider}. Se aplican al solver
     * vía {@code constraintProviderCustomProperties} de Timefold, no por inyección de Spring.
     * Default: sobrecupo ≫ misma comisión/edificio > misma comisión/aula.
     */
    @Getter
    @Setter
    public static class Weights {
        private int overcrowding = 100_000;
        private int sameCommissionDiffRoom = 2_000;
        private int sameCommissionDiffBuilding = 4_000;
    }
}
