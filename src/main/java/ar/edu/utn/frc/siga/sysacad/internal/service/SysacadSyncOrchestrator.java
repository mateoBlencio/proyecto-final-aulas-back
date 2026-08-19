package ar.edu.utn.frc.siga.sysacad.internal.service;

import ar.edu.utn.frc.siga.sysacad.internal.exception.SysacadUnavailableException;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadResyncOutcome;

import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import ar.edu.utn.frc.siga.sysacad.api.SysacadViewSyncer;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadSyncOrchestrator {

    private final Map<SysacadView, SysacadViewSyncer> syncersByView = new EnumMap<>(SysacadView.class);
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SysacadSyncOrchestrator(List<SysacadViewSyncer> syncers) {
        syncers.forEach(syncer -> syncersByView.put(syncer.view(), syncer));
    }

    public void sync(SysacadView view) {
        SysacadViewSyncer syncer = syncersByView.get(view);
        if (syncer == null) {
            log.warn("No hay sync registrado para la vista {}", view);
            return;
        }
        syncer.sync();
    }

    public SysacadResyncOutcome resyncAll() {
        if (!running.compareAndSet(false, true)) {
            log.info("Resync manual ignorado: ya hay uno en curso");
            return SysacadResyncOutcome.SUCCESS;
        }
        SysacadView[] views = SysacadView.values();
        int failedViews = 0;
        int connectivityFailures = 0;
        try {
            for (SysacadView view : views) {
                try {
                    sync(view);
                } catch (RuntimeException e) {
                    log.error("Falló el sync de la vista {}: {}", view, e.getMessage());
                    failedViews++;
                    if (e instanceof SysacadUnavailableException) {
                        connectivityFailures++;
                    }
                }
            }
        } finally {
            running.set(false);
        }
        if (failedViews == 0) {
            return SysacadResyncOutcome.SUCCESS;
        }
        if (connectivityFailures == views.length) {
            return SysacadResyncOutcome.CONNECTIVITY_FAILURE;
        }
        return SysacadResyncOutcome.PARTIAL_FAILURE;
    }
}
