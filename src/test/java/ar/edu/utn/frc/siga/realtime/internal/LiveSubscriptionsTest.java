package ar.edu.utn.frc.siga.realtime.internal;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.common.realtime.LiveTopic;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.realtime.config.LiveProperties;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@DisplayName("LiveSubscriptions: conexiones abiertas y reparto")
class LiveSubscriptionsTest {

    private final List<CapturingEmitter> emitters = new ArrayList<>();

    private LiveSubscriptions subscriptions(int maxConnections) {
        LiveProperties properties = new LiveProperties(Duration.ofMinutes(15), Duration.ofSeconds(25),
                Duration.ofMillis(500), maxConnections);
        return new LiveSubscriptions(properties, timeout -> {
            CapturingEmitter emitter = new CapturingEmitter(timeout);
            emitters.add(emitter);
            return emitter;
        });
    }

    @Test
    @DisplayName("Al abrir manda ready y usa el tope de duración configurado")
    void openSendsReady() {
        LiveSubscriptions subscriptions = subscriptions(10);

        subscriptions.open(Map.of(LiveTopic.ALLOCATIONS, BuildingScope.unrestricted()));

        CapturingEmitter emitter = emitters.getFirst();
        assertThat(emitter.getTimeout()).isEqualTo(Duration.ofMinutes(15).toMillis());
        assertThat(emitter.text()).contains("event:ready");
    }

    @Test
    @DisplayName("Reparte cada aviso solo a las conexiones cuyo alcance lo incluye")
    void deliversByScope() {
        LiveSubscriptions subscriptions = subscriptions(10);
        subscriptions.open(Map.of(LiveTopic.ALLOCATIONS, BuildingScope.of(Set.of(1L))));
        subscriptions.open(Map.of(LiveTopic.ALLOCATIONS, BuildingScope.of(Set.of(2L))));
        subscriptions.open(Map.of(LiveTopic.ROOM_REQUESTS, BuildingScope.unrestricted()));

        subscriptions.deliver(new LiveDelivery(LiveTopic.ALLOCATIONS, Set.of(1L), false));

        assertThat(emitters.get(0).text()).contains("event:change\ndata:ALLOCATIONS");
        assertThat(emitters.get(1).text()).doesNotContain("ALLOCATIONS");
        assertThat(emitters.get(2).text()).doesNotContain("ALLOCATIONS");
    }

    @Test
    @DisplayName("Rechaza conexiones nuevas al llegar al tope")
    void rejectsOverCapacity() {
        LiveSubscriptions subscriptions = subscriptions(1);

        assertThat(subscriptions.open(Map.of())).isPresent();
        assertThat(subscriptions.open(Map.of())).isEmpty();
    }

    @Test
    @DisplayName("Una conexión que falla al escribir se descarta y no frena a las demás")
    void dropsBrokenConnections() {
        LiveSubscriptions subscriptions = subscriptions(10);
        subscriptions.open(Map.of(LiveTopic.ALLOCATIONS, BuildingScope.unrestricted()));
        subscriptions.open(Map.of(LiveTopic.ALLOCATIONS, BuildingScope.unrestricted()));
        emitters.get(0).failNextSends = true;

        subscriptions.deliver(new LiveDelivery(LiveTopic.ALLOCATIONS, Set.of(), true));

        assertThat(subscriptions.size()).isEqualTo(1);
        assertThat(emitters.get(1).text()).contains("data:ALLOCATIONS");
    }

    @Test
    @DisplayName("El heartbeat llega a todas las conexiones como comentario")
    void heartbeatReachesEveryone() {
        LiveSubscriptions subscriptions = subscriptions(10);
        subscriptions.open(Map.of());
        subscriptions.open(Map.of(LiveTopic.ALLOCATIONS, BuildingScope.denied()));

        subscriptions.heartbeat();

        assertThat(emitters).allSatisfy(emitter -> assertThat(emitter.text()).contains(":ping"));
    }

    private static final class CapturingEmitter extends SseEmitter {

        private final List<Object> sent = new ArrayList<>();
        private boolean failNextSends;

        private CapturingEmitter(Long timeout) {
            super(timeout);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (failNextSends) throw new IOException("cliente desconectado");
            builder.build().forEach(part -> sent.add(part.getData()));
        }

        @Override
        public void completeWithError(Throwable ex) {
        }

        String text() {
            return sent.stream().map(String::valueOf).collect(Collectors.joining());
        }
    }
}
