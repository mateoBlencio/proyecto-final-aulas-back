package ar.edu.utn.frc.siga.allocation.service.command;

import java.util.List;

import org.springframework.modulith.NamedInterface;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;

/**
 * {@code currentScopeAuthorized}: el llamador ya autorizó al actor sobre las aulas donde están hoy las
 * ocurrencias, así que {@code reallocate} no repite ese chequeo. El de las aulas destino se hace siempre.
 */
@NamedInterface("api")
public record AllocationCommand(List<AllocationItem> items, String observation, AllocationSource source,
                                boolean currentScopeAuthorized) {

    public AllocationCommand(List<AllocationItem> items, String observation, AllocationSource source) {
        this(items, observation, source, false);
    }

    public static AllocationCommand manual(List<AllocationItem> items, String observation) {
        return new AllocationCommand(items, observation, AllocationSource.MANUAL);
    }

    public static AllocationCommand imported(List<AllocationItem> items, String observation) {
        return new AllocationCommand(items, observation, AllocationSource.IMPORTED);
    }

    public static AllocationCommand automatic(List<AllocationItem> items) {
        return automatic(items, null);
    }

    public static AllocationCommand automatic(List<AllocationItem> items, String observation) {
        return new AllocationCommand(items, observation, AllocationSource.AUTOMATIC);
    }

    public AllocationCommand withCurrentScopeAuthorized() {
        return new AllocationCommand(items, observation, source, true);
    }
}
