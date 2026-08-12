package ar.edu.utn.frc.siga.common.util;

/** Calcula el sobrecupo (inscriptos por encima de la capacidad del aula) de forma null-safe. */
public final class Overcrowding {

    private Overcrowding() {
    }

    /**
     * Sobrecupo de {@code enrolled} contra {@code capacity}, con piso en 0.
     * {@code null} si falta alguno de los dos datos: cada caller decide si lo
     * interpreta como "sin sobrecupo" (0/omitir) o lo propaga.
     */
    public static Integer by(Integer enrolled, Integer capacity) {
        if (enrolled == null || capacity == null) {
            return null;
        }
        return Math.max(0, enrolled - capacity);
    }
}
