package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestExpiryServiceImpl")
class RoomRequestExpiryServiceImplTest {

    @Mock
    private RoomRequestItemRepository itemRepository;
    @Mock
    private AcademicEventService academicEventService;

    private RoomRequestExpiryServiceImpl newService() {
        return new RoomRequestExpiryServiceImpl(itemRepository, academicEventService);
    }

    @Test
    @DisplayName("cancela con datos de sistema los ítems vencidos por fecha directa")
    void cancelsItemsExpiredByDate() {
        RoomRequestItem item = RoomRequestItem.builder().id(1L).status(RoomRequestStatus.NEW)
                .date(LocalDate.now().minusDays(1)).build();
        when(itemRepository.findExpiredByDate(any())).thenReturn(List.of(item));
        when(itemRepository.findActiveRegularRoomChangeItems()).thenReturn(List.of());

        int cancelled = newService().expireOverdueItems();

        assertThat(cancelled).isEqualTo(1);
        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.CANCELLED);
        assertThat(item.getDecidedBy()).isEqualTo("SISTEMA");
        assertThat(item.getDecisionReason()).isEqualTo("Vencido: la fecha del pedido ya pasó");
        assertThat(item.getDecidedAt()).isNotNull();
    }

    @Test
    @DisplayName("REGULAR_ROOM_CHANGE vence cuando el endDate del evento recurrente ya pasó")
    void cancelsRegularRoomChangeWithPastEndDate() {
        RoomRequestItem item = RoomRequestItem.builder().id(2L).status(RoomRequestStatus.NEW)
                .sourceRecurringEventId(10L).build();
        when(itemRepository.findExpiredByDate(any())).thenReturn(List.of());
        when(itemRepository.findActiveRegularRoomChangeItems()).thenReturn(List.of(item));
        when(academicEventService.findByIds(anyCollection()))
                .thenReturn(List.of(recurringEvent(10L, LocalDate.now().minusDays(1))));

        int cancelled = newService().expireOverdueItems();

        assertThat(cancelled).isEqualTo(1);
        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.CANCELLED);
    }

    @Test
    @DisplayName("REGULAR_ROOM_CHANGE con endDate nulo no vence")
    void doesNotExpireRegularRoomChangeWithNullEndDate() {
        RoomRequestItem item = RoomRequestItem.builder().id(3L).status(RoomRequestStatus.NEW)
                .sourceRecurringEventId(11L).build();
        when(itemRepository.findExpiredByDate(any())).thenReturn(List.of());
        when(itemRepository.findActiveRegularRoomChangeItems()).thenReturn(List.of(item));
        when(academicEventService.findByIds(anyCollection()))
                .thenReturn(List.of(recurringEvent(11L, null)));

        int cancelled = newService().expireOverdueItems();

        assertThat(cancelled).isZero();
        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.NEW);
    }

    @Test
    @DisplayName("REGULAR_ROOM_CHANGE con endDate futuro no vence")
    void doesNotExpireRegularRoomChangeWithFutureEndDate() {
        RoomRequestItem item = RoomRequestItem.builder().id(4L).status(RoomRequestStatus.NEW)
                .sourceRecurringEventId(12L).build();
        when(itemRepository.findExpiredByDate(any())).thenReturn(List.of());
        when(itemRepository.findActiveRegularRoomChangeItems()).thenReturn(List.of(item));
        when(academicEventService.findByIds(anyCollection()))
                .thenReturn(List.of(recurringEvent(12L, LocalDate.now().plusDays(1))));

        int cancelled = newService().expireOverdueItems();

        assertThat(cancelled).isZero();
        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.NEW);
    }

    private AcademicEventResponseDto recurringEvent(Long id, LocalDate endDate) {
        return new RecurringEventResponseDto(id, EventType.RECURRING, 0, null, 0,
                DayOfWeek.MONDAY, LocalDate.now().minusYears(1), endDate, null, null);
    }
}
