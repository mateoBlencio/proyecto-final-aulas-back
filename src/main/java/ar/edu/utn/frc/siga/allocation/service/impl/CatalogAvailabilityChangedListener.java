package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.academic.event.CatalogAvailabilityChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CatalogAvailabilityChangedListener {

    private final OccurrenceBoardCache boardCache;

    @EventListener
    void on(CatalogAvailabilityChanged event) {
        boardCache.invalidate();
    }
}
