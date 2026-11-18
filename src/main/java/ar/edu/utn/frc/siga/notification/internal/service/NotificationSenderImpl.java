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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    /** Las direcciones del pedido más la casilla configurada ({@code MAIL_REPLY_TO}), sin repetir: la casilla
     *  siempre recibe la respuesta, también cuando el llamador manda las suyas (auxiliares de un edificio). */
    private String resolveReplyTo(NotificationRequest request) {
        Map<String, String> addresses = new LinkedHashMap<>();
        List<String> requested = request.replyTo() == null ? List.of() : request.replyTo();
        for (String address : requested) {
            addAddress(addresses, address);
        }
        addAddress(addresses, properties.getEmail().getReplyTo());
        return addresses.isEmpty() ? null : String.join(",", addresses.values());
    }

    private static void addAddress(Map<String, String> addresses, String address) {
        if (address != null && !address.isBlank()) {
            addresses.putIfAbsent(address.strip().toLowerCase(Locale.ROOT), address.strip());
        }
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
