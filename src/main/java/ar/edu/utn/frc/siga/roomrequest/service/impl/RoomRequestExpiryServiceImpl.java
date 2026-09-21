package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestExpiryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private final RoomRequestItemRepository itemRepository;
    private final AcademicEventService academicEventService;

    @Override
    @Transactional
    public int expireOverdueItems() {
        LocalDate today = LocalDate.now();

        List<RoomRequestItem> expired = new ArrayList<>(itemRepository.findExpiredByDate(today));
        expired.addAll(expiredRegularRoomChangeItems(today));

        LocalDateTime now = LocalDateTime.now();
        expired.forEach(item -> item.decide(RoomRequestStatus.CANCELLED, SYSTEM_ACTOR, EXPIRY_REASON, now));

        log.info("Vencimiento automático de pedidos de aula: {} cancelado(s)", expired.size());
        return expired.size();
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
