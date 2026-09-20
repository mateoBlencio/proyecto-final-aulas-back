package ar.edu.utn.frc.siga.notification.service.impl;

import ar.edu.utn.frc.siga.notification.model.OutboundNotification;
import ar.edu.utn.frc.siga.notification.repository.OutboundNotificationRepository;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestResolved;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
class RoomRequestResolvedListener {

    private static final String CHANNEL_EMAIL = "EMAIL";

    private final OutboundNotificationRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Corre después del commit de {@code notify} (entrega at-least-once vía {@code event_publication}):
     * una falla acá nunca revierte la transición del pedido a RESOLVED.
     */
    @ApplicationModuleListener
    void on(RoomRequestResolved event) {
        OutboundNotification notification = repository.save(OutboundNotification.builder()
                .recipient(event.teacherEmail())
                .channel(CHANNEL_EMAIL)
                .payload(objectMapper.writeValueAsString(event))
                .build());
        log.info("Notificación pendiente de envío registrada: id={}, itemId={}",
                notification.getId(), event.itemId());
    }
}
