package ar.edu.utn.frc.siga.notification.internal.service;

import ar.edu.utn.frc.siga.notification.api.NotificationChannel;
import ar.edu.utn.frc.siga.notification.api.NotificationRecipient;
import ar.edu.utn.frc.siga.notification.api.NotificationRequest;
import ar.edu.utn.frc.siga.notification.api.NotificationResult;
import ar.edu.utn.frc.siga.notification.api.NotificationSender;
import ar.edu.utn.frc.siga.notification.internal.channel.ChannelRegistry;
import ar.edu.utn.frc.siga.notification.internal.config.NotificationProperties;
import ar.edu.utn.frc.siga.notification.internal.model.Notification;
import ar.edu.utn.frc.siga.notification.internal.render.NotificationRenderer;
import ar.edu.utn.frc.siga.notification.internal.render.RenderedNotification;
import ar.edu.utn.frc.siga.notification.internal.repository.NotificationRepository;
import ar.edu.utn.frc.siga.settings.api.SettingsReader;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
class NotificationSenderImpl implements NotificationSender {

    private static final Map<NotificationChannel, SettingKey> CHANNEL_TOGGLES =
            Map.of(NotificationChannel.EMAIL, SettingKey.NOTIFICATIONS_CHANNEL_EMAIL_ENABLED);

    private final SettingsReader settingsReader;
    private final ChannelRegistry channelRegistry;
    private final NotificationRenderer renderer;
    private final NotificationRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationProperties properties;

    @Override
    @Transactional
    public NotificationResult send(NotificationRequest request) {
        int totalPairs = request.recipients().size() * NotificationChannel.values().length;
        if (!settingsReader.getBoolean(SettingKey.NOTIFICATIONS_ENABLED)) {
            return new NotificationResult(0, totalPairs);
        }

        RenderedNotification rendered = renderer.render(request.template(), request.model());
        String replyTo = resolveReplyTo(request);
        List<Long> queuedIds = new ArrayList<>();
        int skipped = 0;

        for (NotificationChannel channel : NotificationChannel.values()) {
            if (!settingsReader.getBoolean(CHANNEL_TOGGLES.get(channel))) {
                skipped += request.recipients().size();
                continue;
            }
            boolean channelImplemented = channelRegistry.find(channel).isPresent();
            for (NotificationRecipient recipient : request.recipients()) {
                if (!channelImplemented) {
                    Notification discarded = newNotification(request, channel, recipient, rendered, replyTo);
                    discarded.markDiscarded("canal sin implementación disponible");
                    repository.save(discarded);
                    log.warn("Canal {} habilitado sin ChannelSender registrado, notificación descartada", channel);
                    skipped++;
                    continue;
                }
                if (repository.existsByIdempotencyKeyAndChannelAndRecipient(
                        request.idempotencyKey(), channel, recipient.email())) {
                    skipped++;
                    continue;
                }
                Notification notification = repository.save(newNotification(request, channel, recipient, rendered, replyTo));
                queuedIds.add(notification.getId());
            }
        }

        if (!queuedIds.isEmpty()) {
            eventPublisher.publishEvent(new NotificationsQueuedEvent(queuedIds));
        }
        return new NotificationResult(queuedIds.size(), skipped);
    }

    private String resolveReplyTo(NotificationRequest request) {
        List<String> addresses = request.replyTo() == null ? List.of() : request.replyTo();
        if (addresses.isEmpty()) {
            String fallback = properties.getEmail().getReplyTo();
            return fallback == null || fallback.isBlank() ? null : fallback.strip();
        }
        return String.join(",", addresses);
    }

    private static Notification newNotification(
            NotificationRequest request,
            NotificationChannel channel,
            NotificationRecipient recipient,
            RenderedNotification rendered,
            String replyTo) {
        return Notification.builder()
                .template(request.template().name())
                .channel(channel)
                .recipient(recipient.email())
                .recipientName(recipient.displayName())
                .subject(rendered.subject())
                .body(rendered.body())
                .replyTo(replyTo)
                .idempotencyKey(request.idempotencyKey())
                .nextAttemptAt(Instant.now())
                .build();
    }
}
