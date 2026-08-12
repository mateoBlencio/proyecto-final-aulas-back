package ar.edu.utn.frc.siga.optimizer.config;

import ai.timefold.solver.core.config.solver.EnvironmentMode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Propiedades de configuración del optimizer, bajo el prefijo {@code siga.optimizer} de application.yaml. */
@Getter
@Setter
@ConfigurationProperties(prefix = "siga.optimizer")
public class OptimizerProperties {

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

    private Weights weights = new Weights();

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
