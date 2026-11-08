package ar.edu.utn.frc.siga.sysacad.internal.write;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

/** Espacia los pedidos a SysAcad para no pasar el máximo por minuto. Una instancia por corrida. */
public class RequestPacer {

    private final Clock clock;
    private final Consumer<Duration> sleeper;
    private final Duration interval;
    private Instant lastRequestAt;

    public RequestPacer(Clock clock, Consumer<Duration> sleeper, int maxRequestsPerMinute) {
        this.clock = clock;
        this.sleeper = sleeper;
        this.interval = Duration.ofMinutes(1).dividedBy(maxRequestsPerMinute);
    }

    public static RequestPacer sleepingThread(Clock clock, int maxRequestsPerMinute) {
        return new RequestPacer(clock, RequestPacer::sleep, maxRequestsPerMinute);
    }

    public void acquire() {
        Instant now = clock.instant();
        Instant next = lastRequestAt == null || now.isAfter(lastRequestAt.plus(interval))
                ? now : lastRequestAt.plus(interval);
        Duration wait = Duration.between(now, next);
        if (!wait.isZero()) {
            sleeper.accept(wait);
        }
        lastRequestAt = next;
    }

    private static void sleep(Duration wait) {
        try {
            Thread.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Espera entre pedidos a SysAcad interrumpida", e);
        }
    }
}
