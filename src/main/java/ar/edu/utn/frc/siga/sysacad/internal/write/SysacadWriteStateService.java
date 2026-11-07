package ar.edu.utn.frc.siga.sysacad.internal.write;

import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadWriteState;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadWriteStateRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Fila única de estado del job de envío; cada cambio en su propia transacción. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadWriteStateService {

    private static final int ERROR_MAX = 1000;
    private static final int RESULT_MAX = 40;

    private final SysacadWriteStateRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public SysacadWriteState current() {
        return load();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startRun(Instant now) {
        SysacadWriteState state = load();
        state.setLastRunStartedAt(now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishRun(Instant now, String result, String error) {
        SysacadWriteState state = load();
        state.setLastRunFinishedAt(now);
        state.setLastResult(truncate(result, RESULT_MAX));
        state.setLastError(truncate(error, ERROR_MAX));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordViewRead(Instant now) {
        load().setLastViewReadAt(now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markOutage(Instant now) {
        SysacadWriteState state = load();
        if (state.getOutageSince() == null) {
            state.setOutageSince(now);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearOutage() {
        SysacadWriteState state = load();
        state.setOutageSince(null);
        state.setLastOutageAlertAt(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOutageAlert(Instant now) {
        load().setLastOutageAlertAt(now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void pauseUntil(Instant until) {
        load().setPausedUntil(until);
    }

    private SysacadWriteState load() {
        return repository.findById(SysacadWriteState.SINGLETON_ID).orElseGet(() -> {
            SysacadWriteState created = new SysacadWriteState();
            return repository.save(created);
        });
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
