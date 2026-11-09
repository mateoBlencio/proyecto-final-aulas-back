package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.audit.internal.AuditDescriptionProbe;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.roomrequest.event.RoomRequestItemAccepted;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestExpiryServiceImpl")
class RoomRequestExpiryServiceImplTest {

    @Mock
    private RoomRequestItemRepository itemRepository;
    @Mock
    private AcademicEventService academicEventService;
    @Mock
    private OccurrenceService occurrenceService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private RoomRequestExpiryServiceImpl newService() {
        return new RoomRequestExpiryServiceImpl(itemRepository, academicEventService, occurrenceService, eventPublisher);
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

    @Test
    @DisplayName("IN_EVALUATION con aula y sin ninguna notificación vence con el motivo de no notificado")
    void cancelsAssignedNeverNotifiedItemWithOwnReason() {
        RoomRequestItem item = inEvaluationWithRoom(5L, 0);
        when(itemRepository.findExpiredByDate(any())).thenReturn(List.of(item));
        when(itemRepository.findActiveRegularRoomChangeItems()).thenReturn(List.of());

        newService().expireOverdueItems();

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.CANCELLED);
        assertThat(item.getDecidedBy()).isEqualTo("SISTEMA");
        assertThat(item.getDecisionReason()).isEqualTo("Vencido sin notificar al docente");
    }

    @Test
    @DisplayName("IN_EVALUATION con aula pero ya notificado una vez (reabierto) vence con el motivo general")
    void cancelsReopenedItemWithGeneralReason() {
        RoomRequestItem item = inEvaluationWithRoom(6L, 1);
        when(itemRepository.findExpiredByDate(any())).thenReturn(List.of(item));
        when(itemRepository.findActiveRegularRoomChangeItems()).thenReturn(List.of());

        newService().expireOverdueItems();

        assertThat(item.getDecisionReason()).isEqualTo("Vencido: la fecha del pedido ya pasó");
    }

    @Test
    @DisplayName("IN_EVALUATION sin aula vence con el motivo general")
    void cancelsUnassignedInEvaluationItemWithGeneralReason() {
        RoomRequestItem item = RoomRequestItem.builder().id(7L).status(RoomRequestStatus.IN_EVALUATION)
                .date(LocalDate.now().minusDays(1)).build();
        when(itemRepository.findExpiredByDate(any())).thenReturn(List.of(item));
        when(itemRepository.findActiveRegularRoomChangeItems()).thenReturn(List.of());

        newService().expireOverdueItems();

        assertThat(item.getDecisionReason()).isEqualTo("Vencido: la fecha del pedido ya pasó");
    }

    @Test
    @DisplayName("NOTIFIED con la fecha pasada pasa a RESOLVED con actor SISTEMA y publica la aceptación")
    void acceptsNotifiedItemWhoseDatePassed() {
        RoomRequestItem item = RoomRequestItem.builder().id(8L).status(RoomRequestStatus.NOTIFIED)
                .date(LocalDate.now().minusDays(1)).notificationRound(1).build();
        when(itemRepository.findExpiredByDate(any())).thenReturn(List.of(item));
        when(itemRepository.findActiveRegularRoomChangeItems()).thenReturn(List.of());

        newService().expireOverdueItems();

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.RESOLVED);
        assertThat(item.getAcceptedBy()).isEqualTo("SISTEMA");
        assertThat(item.getAcceptedAt()).isNotNull();
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.any(RoomRequestItemAccepted.class));
    }

    @Test
    @DisplayName("acepta los NOTIFIED con plazo vencido con actor SISTEMA y publica el evento")
    void acceptsOverdueNotifiedItems() {
        RoomRequestItem item = RoomRequestItem.builder().id(9L).status(RoomRequestStatus.NOTIFIED)
                .acceptanceDeadline(Instant.now().minusSeconds(60)).notificationRound(1).build();
        when(itemRepository.findNotifiedWithDeadlineUntil(any())).thenReturn(List.of(item));

        int accepted = newService().acceptOverdueNotifiedItems();

        assertThat(accepted).isEqualTo(1);
        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.RESOLVED);
        assertThat(item.getDecidedBy()).isEqualTo("SISTEMA");
        assertThat(item.getAcceptedBy()).isEqualTo("SISTEMA");
        assertThat(item.getAcceptanceDeadline()).isNull();
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.any(RoomRequestItemAccepted.class));
    }

    @Test
    @DisplayName("sin NOTIFIED vencidos no acepta nada")
    void acceptsNothingWhenNoneOverdue() {
        when(itemRepository.findNotifiedWithDeadlineUntil(any())).thenReturn(List.of());

        assertThat(newService().acceptOverdueNotifiedItems()).isZero();
    }

    private RoomRequestItem inEvaluationWithRoom(Long id, int round) {
        RoomRequestItem item = RoomRequestItem.builder().id(id).status(RoomRequestStatus.IN_EVALUATION)
                .request(RoomRequest.builder().type(RoomRequestType.ONE_TIME_ROOM_CHANGE).build())
                .date(LocalDate.now().minusDays(1)).notificationRound(round).build();
        item.getAllocations().add(RoomRequestItemAllocation.builder().occurrenceId(id * 10).classroomId(1L)
                .originalCaptured(true).originalClassroomId(1L).position(1).build());
        return item;
    }

    private RoomRequestItem reopenedRegular(Long id, RoomRequestType type, LocalDate reopenedOn,
            RoomRequestItemAllocation... rows) {
        return reopened(id, type, reopenedOn, 1, rows);
    }

    private RoomRequestItem reopened(Long id, RoomRequestType type, LocalDate reopenedOn, int round,
            RoomRequestItemAllocation... rows) {
        RoomRequestItem item = RoomRequestItem.builder().id(id).status(RoomRequestStatus.IN_EVALUATION)
                .request(RoomRequest.builder().type(type).build())
                .date(LocalDate.now().minusDays(1)).notificationRound(round).reopenedFromNotifiedOn(reopenedOn).build();
        item.getAllocations().addAll(List.of(rows));
        when(itemRepository.findExpiredByDate(any())).thenReturn(List.of(item));
        when(itemRepository.findActiveRegularRoomChangeItems()).thenReturn(List.of());
        return item;
    }

    private static RoomRequestItemAllocation row(Long occurrenceId, Long classroomId, Long originalClassroomId) {
        return RoomRequestItemAllocation.builder().occurrenceId(occurrenceId).classroomId(classroomId)
                .originalCaptured(true).originalClassroomId(originalClassroomId)
                .originalSource(AllocationSource.MANUAL).position(1).build();
    }

    private static OccurrenceSlotDto slot(Long occurrenceId, LocalDate date) {
        return new OccurrenceSlotDto(occurrenceId, 1L, date, java.time.LocalTime.of(9, 0),
                java.time.LocalTime.of(10, 30), OccurrenceStatus.NEEDS_ROOM, 30, 1, null, null, null, null);
    }

    @Test
    @DisplayName("reabierto que se quedó en el aula notificada en una fecha desde la reapertura: pasa a RESOLVED y publica la aceptación")
    void acceptsReopenedItemThatStayedInNotifiedClassroom() {
        LocalDate reopenedOn = LocalDate.now().minusDays(3);
        RoomRequestItem item = reopenedRegular(20L, RoomRequestType.REGULAR_ROOM_CHANGE, reopenedOn,
                row(1L, 200L, 100L), row(2L, 100L, 100L));
        when(occurrenceService.findSlots(List.of(1L, 2L))).thenReturn(List.of(
                slot(1L, reopenedOn.plusDays(1)), slot(2L, reopenedOn.plusDays(2))));

        newService().expireOverdueItems();

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.RESOLVED);
        assertThat(item.getAcceptedBy()).isEqualTo("SISTEMA");
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.any(RoomRequestItemAccepted.class));
    }

    @Test
    @DisplayName("reabierto cuyas fechas volvieron todas al original: pasa a CANCELLED con el motivo general")
    void cancelsReopenedItemWhoseDatesAllReturned() {
        LocalDate reopenedOn = LocalDate.now().minusDays(3);
        RoomRequestItem item = reopenedRegular(21L, RoomRequestType.ONE_TIME_ROOM_CHANGE, reopenedOn,
                row(1L, 100L, 100L));
        when(occurrenceService.findSlots(List.of(1L))).thenReturn(List.of(slot(1L, reopenedOn.plusDays(1))));

        newService().expireOverdueItems();

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.CANCELLED);
        assertThat(item.getDecisionReason()).isEqualTo("Vencido: la fecha del pedido ya pasó");
        verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any(RoomRequestItemAccepted.class));
    }

    @Test
    @DisplayName("reabierto cuya única fila distinta del original es anterior a la reapertura: pasa a CANCELLED")
    void cancelsReopenedItemWhenOnlyPastRowsDiffer() {
        LocalDate reopenedOn = LocalDate.now().minusDays(3);
        RoomRequestItem item = reopenedRegular(22L, RoomRequestType.REGULAR_ROOM_CHANGE, reopenedOn,
                row(1L, 200L, 100L), row(2L, 100L, 100L));
        when(occurrenceService.findSlots(List.of(1L, 2L))).thenReturn(List.of(
                slot(1L, reopenedOn.minusDays(7)), slot(2L, reopenedOn.plusDays(1))));

        newService().expireOverdueItems();

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.CANCELLED);
    }

    @Test
    @DisplayName("reasignado sin renotificar (reabierto_desde nulo): pasa a CANCELLED sin consultar las ocurrencias")
    void cancelsReopenedItemReassignedWithoutRenotify() {
        RoomRequestItem item = reopenedRegular(23L, RoomRequestType.REGULAR_ROOM_CHANGE, null,
                row(1L, 200L, 100L));

        newService().expireOverdueItems();

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.CANCELLED);
        org.mockito.Mockito.verifyNoInteractions(occurrenceService);
    }

    @Test
    @DisplayName("IN_EVALUATION que nunca se notificó (ronda 0) con reabierto_desde fijado: sigue cancelándose")
    void cancelsNeverNotifiedItemEvenWithReopenedMarker() {
        RoomRequestItem item = reopened(24L, RoomRequestType.REGULAR_ROOM_CHANGE, LocalDate.now().minusDays(3), 0,
                row(1L, 200L, 100L));

        newService().expireOverdueItems();

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.CANCELLED);
        assertThat(item.getDecisionReason()).isEqualTo("Vencido sin notificar al docente");
    }

    @Test
    @DisplayName("reabierto de un tipo creado por SIGA: pasa a CANCELLED aunque la fila difiera del original")
    void cancelsReopenedItemOfTypeWithoutOriginal() {
        RoomRequestItem item = reopenedRegular(25L, RoomRequestType.FINAL_EXAM, LocalDate.now().minusDays(3),
                row(1L, 200L, 100L));

        newService().expireOverdueItems();

        assertThat(item.getStatus()).isEqualTo(RoomRequestStatus.CANCELLED);
    }

    @ParameterizedTest
    @CsvSource({"0,0 ítems", "1,1 ítem", "2,2 ítems"})
    @DisplayName("la descripción de la operación lleva la cantidad de ítems con la concordancia correcta")
    void describesExpiredCount(int count, String expectedCount) {
        List<RoomRequestItem> expired = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            expired.add(RoomRequestItem.builder().id(100L + i).status(RoomRequestStatus.NEW)
                    .date(LocalDate.now().minusDays(1)).build());
        }
        when(itemRepository.findExpiredByDate(any())).thenAnswer(inv -> {
            AuditDescriptionProbe.stamp();
            return expired;
        });
        when(itemRepository.findActiveRegularRoomChangeItems()).thenReturn(List.of());
        AuditDescriptionProbe probe = new AuditDescriptionProbe();

        probe.audited(newService()).expireOverdueItems();

        assertThat(probe.rewrittenDescription())
                .isEqualTo("Vencimiento automático de " + expectedCount + " de solicitudes de aula");
    }

    private AcademicEventResponseDto recurringEvent(Long id, LocalDate endDate) {
        return new RecurringEventResponseDto(id, EventType.RECURRING, 0, null, 0,
                DayOfWeek.MONDAY, LocalDate.now().minusYears(1), endDate, null, null);
    }
}
