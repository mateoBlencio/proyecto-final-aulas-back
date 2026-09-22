package ar.edu.utn.frc.siga.notification.internal.service;

import ar.edu.utn.frc.siga.notification.api.NotificationRecipient;
import ar.edu.utn.frc.siga.notification.internal.channel.ChannelRegistry;
import ar.edu.utn.frc.siga.notification.internal.channel.ChannelSender;
import ar.edu.utn.frc.siga.notification.internal.config.NotificationProperties;
import ar.edu.utn.frc.siga.notification.internal.model.Notification;
import ar.edu.utn.frc.siga.notification.internal.model.NotificationStatus;
import ar.edu.utn.frc.siga.notification.internal.render.RenderedNotification;
import ar.edu.utn.frc.siga.notification.internal.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
class NotificationDispatcher {

    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    private final NotificationRepository repository;
    private final ChannelRegistry channelRegistry;
    private final NotificationProperties properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(Long id) {
        Notification notification = repository.findById(id).orElse(null);
        if (notification == null || notification.getStatus() != NotificationStatus.PENDING) {
            return;
        }

        Optional<ChannelSender> sender = channelRegistry.find(notification.getChannel());
        if (sender.isEmpty()) {
            notification.markDiscarded("canal sin implementación disponible");
            log.warn("Canal {} sin ChannelSender registrado al despachar la notificación {}",
                    notification.getChannel(), id);
            return;
        }

        try {
            sender.get().send(
                    new RenderedNotification(notification.getSubject(), notification.getBody()),
                    new NotificationRecipient(notification.getRecipientName(), notification.getRecipient()));
            notification.markSent(Instant.now());
        } catch (RuntimeException e) {
            Duration backoff = backoffFor(notification.getAttempts() + 1);
            notification.registerFailure(e.getMessage(), backoff, properties.getMaxAttempts());
            log.warn("Fallo al enviar la notificación {} (intento {}): {}",
                    id, notification.getAttempts(), e.getMessage());
        }
    }

    static Duration backoffFor(int attempt) {
        long minutes = 1L << (attempt - 1);
        return Duration.ofMinutes(minutes).compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : Duration.ofMinutes(minutes);
    }
}
