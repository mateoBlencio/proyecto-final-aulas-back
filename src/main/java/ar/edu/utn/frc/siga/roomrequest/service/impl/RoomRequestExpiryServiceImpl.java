package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.audit.AuditOperation;
import ar.edu.utn.frc.siga.audit.AuditOperations;
import ar.edu.utn.frc.siga.common.util.Plurals;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItemAllocation;
import ar.edu.utn.frc.siga.roomrequest.event.RoomRequestItemAccepted;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestExpiryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomRequestExpiryServiceImpl implements RoomRequestExpiryService {

    private static final String SYSTEM_ACTOR = "SISTEMA";
    private static final String EXPIRY_REASON = "Vencido: la fecha del pedido ya pasó";
    private static final String NOT_NOTIFIED_REASON = "Vencido sin notificar al docente";

    private final RoomRequestItemRepository itemRepository;
    private final AcademicEventService academicEventService;
    private final OccurrenceService occurrenceService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    @AuditOperation("Vencimiento automático de solicitudes de aula")
    public int expireOverdueItems() {
        LocalDate today = LocalDate.now();

        List<RoomRequestItem> expired = new ArrayList<>(itemRepository.findExpiredByDate(today));
        expired.addAll(expiredRegularRoomChangeItems(today));

        Instant now = Instant.now();
        int accepted = 0;
        for (RoomRequestItem item : expired) {
            if (item.getStatus() == RoomRequestStatus.NOTIFIED || staysInNotifiedClassroom(item)) {
                accept(item, now);
                accepted++;
            } else {
                item.decide(RoomRequestStatus.CANCELLED, SYSTEM_ACTOR, cancellationReason(item), now);
            }
        }

        AuditOperations.describe("Vencimiento automático de " + Plurals.count(expired.size(), "ítem", "ítems")
                + " de solicitudes de aula");
        log.info("Vencimiento automático de pedidos de aula: {} cancelado(s), {} aceptado(s)",
                expired.size() - accepted, accepted);
        return expired.size();
    }

    @Override
    @Transactional
    @AuditOperation("Aceptación automática de solicitudes de aula")
    public int acceptOverdueNotifiedItems() {
        Instant now = Instant.now();
        List<RoomRequestItem> overdue = itemRepository.findNotifiedWithDeadlineUntil(now);
        overdue.forEach(item -> accept(item, now));

        AuditOperations.describe("Aceptación automática de " + Plurals.count(overdue.size(), "ítem", "ítems")
                + " de solicitudes de aula");
        log.info("Aceptación automática de pedidos de aula: {} aceptado(s)", overdue.size());
        return overdue.size();
    }

    private void accept(RoomRequestItem item, Instant now) {
        item.accept(SYSTEM_ACTOR, now);
        eventPublisher.publishEvent(new RoomRequestItemAccepted(item.getId(), SYSTEM_ACTOR, now));
    }

    /** Pedido reabierto cuya clase se quedó en el aula notificada: el docente ya la había visto, así que se acepta en vez de cancelar. */
    private boolean staysInNotifiedClassroom(RoomRequestItem item) {
        LocalDate reopenedOn = item.getReopenedFromNotifiedOn();
        if (item.getStatus() != RoomRequestStatus.IN_EVALUATION || item.getNotificationRound() < 1
                || reopenedOn == null
                || !RoomRequestOriginalRestorer.hasOriginalClassroom(item.getRequest().getType())) {
            return false;
        }
        List<Long> principalIds = item.getAllocations().stream()
                .filter(a -> a.getPosition() == 1)
                .map(RoomRequestItemAllocation::getOccurrenceId)
                .toList();
        if (principalIds.isEmpty()) {
            return false;
        }
        Set<Long> relevantIds = occurrenceService.findSlots(principalIds).stream()
                .filter(slot -> !slot.date().isBefore(reopenedOn))
                .map(OccurrenceSlotDto::occurrenceId)
                .collect(Collectors.toSet());
        return item.keepsNotifiedClassroom(relevantIds);
    }

    private static String cancellationReason(RoomRequestItem item) {
        boolean neverNotified = item.getStatus() == RoomRequestStatus.IN_EVALUATION
                && item.getNotificationRound() == 0
                && !item.getAllocations().isEmpty();
        return neverNotified ? NOT_NOTIFIED_REASON : EXPIRY_REASON;
    }

    private List<RoomRequestItem> expiredRegularRoomChangeItems(LocalDate today) {
        List<RoomRequestItem> candidates = itemRepository.findActiveRegularRoomChangeItems();
        if (candidates.isEmpty()) {
            return List.of();
        }

        Set<Long> eventIds = candidates.stream()
                .map(RoomRequestItem::getSourceRecurringEventId)
                .collect(Collectors.toSet());
        Map<Long, LocalDate> endDateByEventId = new HashMap<>();
        academicEventService.findByIds(eventIds).stream()
                .filter(RecurringEventResponseDto.class::isInstance)
                .map(RecurringEventResponseDto.class::cast)
                .forEach(event -> endDateByEventId.put(event.id(), event.endDate()));

        return candidates.stream()
                .filter(item -> {
                    LocalDate endDate = endDateByEventId.get(item.getSourceRecurringEventId());
                    return endDate != null && endDate.isBefore(today);
                })
                .toList();
    }
}
