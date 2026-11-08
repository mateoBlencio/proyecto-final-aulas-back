package ar.edu.utn.frc.siga.sysacad.internal.write;

import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadWriteMode;
import ar.edu.utn.frc.siga.sysacad.internal.service.SysacadSyncLock;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
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
    private final SysacadProperties properties;
    private final Clock clock;

    private ScheduledExecutorService executor;

    public SysacadWriteJob(SysacadWriteRun run, SysacadWriteStateService stateService,
            SysacadAlertService alertService, SysacadSyncLock syncLock, SysacadProperties properties, Clock clock) {
        this.run = run;
        this.stateService = stateService;
        this.alertService = alertService;
        this.syncLock = syncLock;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public synchronized void start() {
        SysacadProperties.Write config = properties.getWrite();
        if (executor != null || config.mode() == SysacadWriteMode.OFF) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sysacad-write-job");
            thread.setDaemon(true);
            return thread;
        });
        long delayMillis = config.jobDelay().toMillis();
        executor.scheduleWithFixedDelay(this::tick, delayMillis, delayMillis, TimeUnit.MILLISECONDS);
        log.info("Job de envío de aulas a SysAcad agendado en modo {} cada {}", config.mode(), config.jobDelay());
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

    private void tick() {
        if (!syncLock.tryLock()) {
            log.info("Corrida de envío a SysAcad salteada: el sync está en curso");
            return;
        }
        try {
            Instant previousRunEnd = stateService.current().getLastRunFinishedAt();
            RunReport report = run.run(clock.instant());
            log.info("Corrida de envío a SysAcad: {} ({} horarios a revisión, {} errores)", report.result(),
                    report.reviewed().size(), report.errors().size());
            sendAlerts(previousRunEnd);
        } catch (Throwable t) {
            log.error("Falló la corrida de envío a SysAcad", t);
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
