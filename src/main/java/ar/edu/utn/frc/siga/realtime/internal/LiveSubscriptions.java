package ar.edu.utn.frc.siga.realtime.internal;

import ar.edu.utn.frc.siga.common.realtime.LiveTopic;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.realtime.config.LiveProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Conexiones SSE abiertas, cada una con el alcance por tema que tenía su usuario al conectarse. */
@Slf4j
@Component
public class LiveSubscriptions {

    static final String READY_EVENT = "ready";
    static final String CHANGE_EVENT = "change";

    private final LiveProperties properties;
    private final Function<Long, SseEmitter> emitterFactory;
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    @Autowired
    public LiveSubscriptions(LiveProperties properties) {
        this(properties, SseEmitter::new);
    }

    LiveSubscriptions(LiveProperties properties, Function<Long, SseEmitter> emitterFactory) {
        this.properties = properties;
        this.emitterFactory = emitterFactory;
    }

    /** Devuelve vacío si se llegó al tope de conexiones. */
    public Optional<SseEmitter> open(Map<LiveTopic, BuildingScope> scopes) {
        if (subscriptions.size() >= properties.maxConnections()) {
            log.warn("Conexión de avisos rechazada: se llegó al tope de {} conexiones", properties.maxConnections());
            return Optional.empty();
        }
        SseEmitter emitter = emitterFactory.apply(properties.maxConnectionDuration().toMillis());
        Subscription subscription = new Subscription(emitter, Map.copyOf(scopes));
        emitter.onCompletion(() -> subscriptions.remove(subscription));
        emitter.onTimeout(() -> {
            subscriptions.remove(subscription);
            emitter.complete();
        });
        emitter.onError(error -> subscriptions.remove(subscription));
        if (!send(subscription, SseEmitter.event().name(READY_EVENT).data(""))) {
            return Optional.empty();
        }
        subscriptions.add(subscription);
        return Optional.of(emitter);
    }

    void deliver(LiveDelivery delivery) {
        for (Subscription subscription : subscriptions) {
            if (delivery.reaches(subscription.scopes().get(delivery.topic()))) {
                send(subscription, SseEmitter.event().name(CHANGE_EVENT).data(delivery.topic().name()));
            }
        }
    }

    void heartbeat() {
        for (Subscription subscription : subscriptions) {
            send(subscription, SseEmitter.event().comment("ping"));
        }
    }

    int size() {
        return subscriptions.size();
    }

    private boolean send(Subscription subscription, SseEmitter.SseEventBuilder event) {
        try {
            subscription.emitter().send(event);
            return true;
        } catch (IOException | IllegalStateException e) {
            subscriptions.remove(subscription);
            subscription.emitter().completeWithError(e);
            return false;
        }
    }

    private record Subscription(SseEmitter emitter, Map<LiveTopic, BuildingScope> scopes) {
    }
}
