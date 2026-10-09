package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.audit.AuditOperation;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;
import ar.edu.utn.frc.siga.common.security.SystemScope;
import ar.edu.utn.frc.siga.events.model.OccurrenceVacated;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class OccurrenceVacatedListener {

    private final AllocationService allocationService;

    @ApplicationModuleListener
    @AuditOperation("Liberación de aula por ocurrencia desocupada")
    void on(OccurrenceVacated event) {
        SystemScope.run(() -> allocationService.deallocate(new DeallocationCommand(
                List.of(new AllocationTarget.Occurrences(List.of(event.occurrenceId()))), null)));
    }
}
