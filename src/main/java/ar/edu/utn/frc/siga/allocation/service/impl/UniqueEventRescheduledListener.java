package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.events.event.UniqueEventRescheduled;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class UniqueEventRescheduledListener {

    private final AllocationService allocationService;

    @EventListener
    void on(UniqueEventRescheduled event) {
        allocationService.revalidateRescheduled(event.occurrenceIds(), event.observation());
    }
}
