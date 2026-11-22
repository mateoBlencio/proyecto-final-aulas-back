package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.events.event.UniqueEventRescheduled;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Mantiene la fecha de los pedidos atados a un evento único que se reprogramó. */
@Component
@RequiredArgsConstructor
class RoomRequestRescheduleListener {

    private final RoomRequestItemRepository itemRepository;

    @EventListener
    void on(UniqueEventRescheduled event) {
        itemRepository.findByAllocationOccurrenceIdIn(event.occurrenceIds())
                .forEach(item -> item.reschedule(event.date()));
    }
}
