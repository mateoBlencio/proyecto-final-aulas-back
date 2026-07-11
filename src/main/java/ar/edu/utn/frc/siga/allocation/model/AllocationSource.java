package ar.edu.utn.frc.siga.allocation.model;

/**
 * Origen de una {@link Allocation}: no es un parámetro que decida el cliente, lo estampa
 * {@code allocation} según el intent method invocado (1 caso de uso → 1 source).
 */
public enum AllocationSource {
    /** La decidió una persona por pantalla (asignación/reasignación manual). */
    MANUAL,
    /** La produjo el solver (Timefold, módulo solver) al confirmar una preview. */
    AUTOMATIC,
    /** Vino de la importación masiva de Excel (módulo excelimport). */
    IMPORTED
}
