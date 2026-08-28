package ar.edu.utn.frc.siga.sysacad.internal.service.impl;

import ar.edu.utn.frc.siga.sysacad.internal.exception.SysacadUnavailableException;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadResyncOutcome;
import ar.edu.utn.frc.siga.sysacad.internal.service.SysacadSyncOrchestrator;

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
public class SysacadSyncOrchestratorImpl implements SysacadSyncOrchestrator {

    private final Map<SysacadView, SysacadViewSyncer> syncersByView = new EnumMap<>(SysacadView.class);
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SysacadSyncOrchestratorImpl(List<SysacadViewSyncer> syncers) {
        syncers.forEach(syncer -> syncersByView.put(syncer.view(), syncer));
    }

    @Override
    public void sync(SysacadView view) {
        SysacadViewSyncer syncer = syncersByView.get(view);
        if (syncer == null) {
            log.warn("No hay sync registrado para la vista {}", view);
            return;
        }
        syncer.sync();
    }

    @Override
    public SysacadResyncOutcome resyncAll() {
        if (!running.compareAndSet(false, true)) {
            log.info("Resync manual ignorado: ya hay uno en curso");
            return SysacadResyncOutcome.SUCCESS;
        }
        int attemptedViews = 0;
        int failedViews = 0;
        int connectivityFailures = 0;
        try {
            for (SysacadView view : SysacadView.values()) {
                // Vistas sin syncer registrado (todavía) no cuentan ni como intento ni como falla — ver
                // sync(view): loguea WARN y las saltea. Sin este chequeo, agregar una vista nueva sin
                // syncer bajaría artificialmente el % de fallas y CONNECTIVITY_FAILURE dejaría de poder
                // darse nunca (siempre habría vistas "sanas" de más en el denominador).
                if (syncersByView.containsKey(view)) {
                    attemptedViews++;
                }
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
        if (attemptedViews > 0 && connectivityFailures == attemptedViews) {
            return SysacadResyncOutcome.CONNECTIVITY_FAILURE;
        }
        return SysacadResyncOutcome.PARTIAL_FAILURE;
    }
}
