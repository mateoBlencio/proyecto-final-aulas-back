package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;
import ar.edu.utn.frc.siga.events.model.OccurrenceVacated;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Primer uso de eventos de aplicación de Spring Modulith en este proyecto: reacciona a que una
 * occurrence liberó su aula, desasignándola del lado de {@code allocation} — separado de la
 * transacción que cambia el status en {@code events}, con reintento automático si falla
 * (registro de publicación de Modulith).
 */
@Component
@RequiredArgsConstructor
class OccurrenceVacatedListener {

    private final AllocationService allocationService;

    @ApplicationModuleListener
    void on(OccurrenceVacated event) {
        allocationService.deallocate(new DeallocationCommand(
                List.of(new AllocationTarget.Occurrences(List.of(event.occurrenceId()))), null));
    }
}
