package ar.edu.utn.frc.siga.auth.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Ventana deslizante (sliding window log) de intentos fallidos para un email. El
 * {@link ArrayDeque} no es thread-safe, así que los métodos que lo tocan están
 * sincronizados: Caffeine sólo protege el mapa (clave -> record), no la mutación de este
 * valor cuando dos requests fallidos concurrentes caen sobre el mismo email.
 */
class LoginRateLimitRecord {

    private final Deque<Instant> failures = new ArrayDeque<>();

    synchronized boolean isRateLimited(Instant now, Duration window, int maxAttempts) {
        prune(now, window);
        return failures.size() >= maxAttempts;
    }

    synchronized void recordFailure(Instant now, Duration window) {
        prune(now, window);
        failures.addLast(now);
    }

    synchronized void reset() {
        failures.clear();
    }

    private void prune(Instant now, Duration window) {
        Instant cutoff = now.minus(window);
        while (!failures.isEmpty() && failures.peekFirst().isBefore(cutoff)) {
            failures.pollFirst();
        }
    }
}
