package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;
import ar.edu.utn.frc.siga.events.model.OccurrenceVacated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OccurrenceVacatedListener")
class OccurrenceVacatedListenerTest {

    @Mock
    private AllocationService allocationService;

    @Test
    @DisplayName("on: desasigna la ocurrencia liberada, dirigida por occurrenceIds (no por evento)")
    void onDesasignaLaOcurrenciaLiberada() {
        OccurrenceVacatedListener listener = new OccurrenceVacatedListener(allocationService);

        listener.on(new OccurrenceVacated(10L));

        ArgumentCaptor<DeallocationCommand> captor = ArgumentCaptor.forClass(DeallocationCommand.class);
        verify(allocationService).deallocate(captor.capture());
        assertThat(captor.getValue().targets()).containsExactly(new AllocationTarget.Occurrences(List.of(10L)));
    }
}
