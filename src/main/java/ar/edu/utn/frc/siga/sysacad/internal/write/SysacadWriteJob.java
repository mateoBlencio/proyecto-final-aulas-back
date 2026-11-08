package ar.edu.utn.frc.siga.sysacad.internal.write;

import ar.edu.utn.frc.siga.settings.api.SettingChangedEvent;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadRuntimeSettings;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadWriteMode;
import ar.edu.utn.frc.siga.sysacad.internal.exception.SysacadConflictException;
import ar.edu.utn.frc.siga.sysacad.internal.service.SysacadSyncLock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import ar.edu.utn.frc.siga.sysacad.internal.write.state.SysacadWriteStateService;
import ar.edu.utn.frc.siga.sysacad.internal.write.review.SysacadAlertService;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadWriteJob implements SmartLifecycle {

    private final SysacadWriteRun run;
    private final SysacadWriteStateService stateService;
    private final SysacadAlertService alertService;
    private final SysacadSyncLock syncLock;
    private final SysacadRuntimeSettings runtimeSettings;
    private final Clock clock;

    private ScheduledExecutorService executor;

    public SysacadWriteJob(SysacadWriteRun run, SysacadWriteStateService stateService,
            SysacadAlertService alertService, SysacadSyncLock syncLock, SysacadRuntimeSettings runtimeSettings, Clock clock) {
        this.run = run;
        this.stateService = stateService;
        this.alertService = alertService;
        this.syncLock = syncLock;
        this.runtimeSettings = runtimeSettings;
        this.clock = clock;
    }

    @Override
    public synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sysacad-write-job");
            thread.setDaemon(true);
            return thread;
        });
        Duration jobDelay = runtimeSettings.jobDelay();
        long delayMillis = jobDelay.toMillis();
        executor.scheduleWithFixedDelay(this::tick, delayMillis, delayMillis, TimeUnit.MILLISECONDS);
        log.info("Job de envío de aulas a SysAcad agendado cada {}", jobDelay);
    }

    @Override
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return executor != null;
    }

    @ApplicationModuleListener
    void onSettingChanged(SettingChangedEvent event) {
        if (event.key() == SettingKey.SYSACAD_WRITE_JOB_DELAY_MINUTES) {
            synchronized (this) {
                if (executor != null) {
                    executor.shutdown();
                    executor = null;
                    start();
                }
            }
        }
    }

    public RunReport runNow() {
        if (runtimeSettings.writeMode() == SysacadWriteMode.OFF) {
            throw new SysacadConflictException("Envío apagado",
                    "El modo de escritura a SysAcad está en OFF: no se puede forzar una corrida");
        }
        return runLocked().orElseThrow(() -> new SysacadConflictException("Corrida en curso",
                "Hay un sync o una corrida de envío en curso, reintentar al terminar"));
    }

    private void tick() {
        if (runtimeSettings.writeMode() == SysacadWriteMode.OFF) {
            return;
        }
        try {
            if (runLocked().isEmpty()) {
                log.info("Corrida de envío a SysAcad salteada: el sync está en curso");
            }
        } catch (Throwable t) {
            log.error("Falló la corrida de envío a SysAcad", t);
        }
    }

    private Optional<RunReport> runLocked() {
        if (!syncLock.tryLock()) {
            return Optional.empty();
        }
        try {
            Instant previousRunEnd = stateService.current().getLastRunFinishedAt();
            RunReport report = run.run(clock.instant());
            log.info("Corrida de envío a SysAcad: {} ({} horarios a revisión, {} errores)", report.result(),
                    report.reviewed().size(), report.errors().size());
            sendAlerts(previousRunEnd);
            return Optional.of(report);
        } finally {
            syncLock.unlock();
        }
    }

    private void sendAlerts(Instant previousRunEnd) {
        try {
            alertService.alert(previousRunEnd);
        } catch (RuntimeException e) {
            log.warn("No se pudo enviar el aviso de SysAcad a Subsecretaría", e);
        }
    }
}
