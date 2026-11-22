package ar.edu.utn.frc.siga.notification.internal.channel;

import ar.edu.utn.frc.siga.notification.api.NotificationChannel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ChannelRegistry {

    private final Map<NotificationChannel, ChannelSender> sendersByChannel;

    public ChannelRegistry(List<ChannelSender> senders) {
        this.sendersByChannel = senders.stream()
                .collect(Collectors.toMap(ChannelSender::channel, Function.identity()));
    }

    public Optional<ChannelSender> find(NotificationChannel channel) {
        return Optional.ofNullable(sendersByChannel.get(channel));
    }
}
