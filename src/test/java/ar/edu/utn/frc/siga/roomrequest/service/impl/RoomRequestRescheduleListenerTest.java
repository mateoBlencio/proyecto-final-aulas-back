package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.events.event.UniqueEventRescheduled;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestRescheduleListener")
class RoomRequestRescheduleListenerTest {

    @Mock
    private RoomRequestItemRepository itemRepository;

    @Test
    @DisplayName("los ítems que apuntan a las ocurrencias reprogramadas pasan a la fecha nueva; consulta por todos los ids")
    void actualizaLaFechaDeLosItemsEncontrados() {
        RoomRequestItem first = RoomRequestItem.builder().date(LocalDate.of(2026, 12, 1)).build();
        RoomRequestItem second = RoomRequestItem.builder().date(LocalDate.of(2026, 12, 1)).build();
        when(itemRepository.findByAllocationOccurrenceIdIn(List.of(10L, 11L))).thenReturn(List.of(first, second));
        var listener = new RoomRequestRescheduleListener(itemRepository);

        listener.on(new UniqueEventRescheduled(3L, List.of(10L, 11L), LocalDate.of(2026, 12, 3), "obs"));

        verify(itemRepository).findByAllocationOccurrenceIdIn(List.of(10L, 11L));
        assertThat(first.getDate()).isEqualTo(LocalDate.of(2026, 12, 3));
        assertThat(second.getDate()).isEqualTo(LocalDate.of(2026, 12, 3));
    }

    @Test
    @DisplayName("sin ítems atados a las ocurrencias → no falla")
    void sinItemsNoHaceNada() {
        when(itemRepository.findByAllocationOccurrenceIdIn(List.of(10L))).thenReturn(List.of());
        var listener = new RoomRequestRescheduleListener(itemRepository);

        listener.on(new UniqueEventRescheduled(3L, List.of(10L), LocalDate.of(2026, 12, 3), null));

        verify(itemRepository).findByAllocationOccurrenceIdIn(List.of(10L));
    }
}
