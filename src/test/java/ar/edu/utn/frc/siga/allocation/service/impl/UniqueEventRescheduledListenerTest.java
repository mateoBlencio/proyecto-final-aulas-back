package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.events.event.UniqueEventRescheduled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UniqueEventRescheduledListener (allocation)")
class UniqueEventRescheduledListenerTest {

    @Mock
    private AllocationService allocationService;

    @Test
    @DisplayName("delega en revalidateRescheduled con los ids de ocurrencia y la observación del evento")
    void delegaEnRevalidateRescheduled() {
        var listener = new UniqueEventRescheduledListener(allocationService);

        listener.on(new UniqueEventRescheduled(3L, List.of(10L, 11L), LocalDate.of(2026, 12, 3), "obs"));

        verify(allocationService).revalidateRescheduled(List.of(10L, 11L), "obs");
    }
}
