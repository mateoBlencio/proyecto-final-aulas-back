package ar.edu.utn.frc.siga.sysacad.internal.service.impl;

import ar.edu.utn.frc.siga.sysacad.internal.exception.SyncError;
import ar.edu.utn.frc.siga.sysacad.internal.exception.SysacadUnavailableException;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadResyncOutcome;
import ar.edu.utn.frc.siga.sysacad.internal.service.SysacadSyncOrchestrator;

import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import ar.edu.utn.frc.siga.sysacad.api.SysacadViewSyncer;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadSyncOrchestratorImpl implements SysacadSyncOrchestrator {

    /** Vistas que deben haber sincronizado bien en la corrida para que la clave pueda correr. */
    private static final Map<SysacadView, Set<SysacadView>> DEPENDENCIES = Map.of(
            SysacadView.AULAS, EnumSet.of(SysacadView.EDIFICIOS),
            SysacadView.MATERIAS, EnumSet.of(SysacadView.ESPECIALIDADES),
            SysacadView.COMISIONES, EnumSet.of(SysacadView.MATERIAS),
            SysacadView.EVENTOS, EnumSet.of(SysacadView.COMISIONES),
            SysacadView.ASIGNACIONES, EnumSet.of(SysacadView.EVENTOS, SysacadView.AULAS));

    private final Map<SysacadView, SysacadViewSyncer> syncersByView = new EnumMap<>(SysacadView.class);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final SysacadCatalogSnapshotFactory snapshotFactory;
    private final SysacadSyncStateService syncStateService;

    public SysacadSyncOrchestratorImpl(List<SysacadViewSyncer> syncers, SysacadCatalogSnapshotFactory snapshotFactory,
            SysacadSyncStateService syncStateService) {
        syncers.forEach(syncer -> syncersByView.put(syncer.view(), syncer));
        this.snapshotFactory = snapshotFactory;
        this.syncStateService = syncStateService;
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
        Set<SysacadView> failed = EnumSet.noneOf(SysacadView.class);
        Set<SysacadView> failedByConnectivity = EnumSet.noneOf(SysacadView.class);
        SysacadCatalogReader snapshot = snapshotFactory.newSnapshot();
        try {
            for (SysacadView view : SysacadView.values()) {
                SysacadViewSyncer syncer = syncersByView.get(view);
                if (syncer == null) {
                    log.warn("No hay sync registrado para la vista {}", view);
                    continue;
                }
                attemptedViews++;
                Optional<SysacadView> brokenDependency = DEPENDENCIES.getOrDefault(view, Set.of()).stream()
                        .filter(failed::contains).findFirst();
                if (brokenDependency.isPresent()) {
                    SysacadView dependency = brokenDependency.get();
                    log.error("Se saltea la vista {}: falló {} en esta corrida", view, dependency);
                    syncStateService.recordFailure(view, SyncError.dependencyFailed(dependency));
                    failed.add(view);
                    failedViews++;
                    if (failedByConnectivity.contains(dependency)) {
                        failedByConnectivity.add(view);
                        connectivityFailures++;
                    }
                    continue;
                }
                try {
                    syncer.sync(snapshot);
                } catch (RuntimeException e) {
                    log.error("Falló el sync de la vista {}: {}", view, e.getClass().getSimpleName());
                    failed.add(view);
                    failedViews++;
                    if (e instanceof SysacadUnavailableException) {
                        failedByConnectivity.add(view);
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
