package ar.edu.utn.frc.siga.allocation.service.command;

import java.util.List;

import org.springframework.modulith.NamedInterface;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;

/**
 * Comando de asignación/reasignación en lote. Las factories son la única forma de
 * construirlo: estampan el {@code source}, la única política que varía por caller y que el
 * cliente nunca decide. El {@code source} determina internamente (en {@code AllocationServiceImpl})
 * el clamp de fechas y si se valida solapamiento —no es un flag del comando.
 */
@NamedInterface("api")
public record AllocationCommand(List<AllocationItem> items, String observation, AllocationSource source) {

    /** Asignación decidida por una persona por pantalla. Valida solapamiento; no toca el pasado. */
    public static AllocationCommand manual(List<AllocationItem> items, String observation) {
        return new AllocationCommand(items, observation, AllocationSource.MANUAL);
    }

    /** Carga masiva desde Excel. Incluye occurrences pasadas y reemplaza el estado existente por diseño. */
    public static AllocationCommand imported(List<AllocationItem> items, String observation) {
        return new AllocationCommand(items, observation, AllocationSource.IMPORTED);
    }

    /** Confirmación de una preview del optimizer: ya validó contra su propio snapshot antes de llamar. */
    public static AllocationCommand automatic(List<AllocationItem> items) {
        return new AllocationCommand(items, null, AllocationSource.AUTOMATIC);
    }
}
