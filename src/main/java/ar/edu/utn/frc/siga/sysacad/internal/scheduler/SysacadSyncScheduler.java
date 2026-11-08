package ar.edu.utn.frc.siga.sysacad.internal.scheduler;

import ar.edu.utn.frc.siga.settings.api.SettingChangedEvent;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadRuntimeSettings;
import ar.edu.utn.frc.siga.sysacad.internal.service.SysacadSyncOrchestrator;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.ZoneId;
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadSyncScheduler {

    private final SysacadSyncOrchestrator orchestrator;
    private final SysacadRuntimeSettings runtimeSettings;
    private final ZoneId zone;
    private final ThreadPoolTaskScheduler taskScheduler;

    private ScheduledFuture<?> scheduled;

    @Autowired
    public SysacadSyncScheduler(SysacadSyncOrchestrator orchestrator, SysacadRuntimeSettings runtimeSettings,
            SysacadProperties properties) {
        this(orchestrator, runtimeSettings, properties.getSync().zone(), new ThreadPoolTaskScheduler());
    }

    SysacadSyncScheduler(SysacadSyncOrchestrator orchestrator, SysacadRuntimeSettings runtimeSettings, ZoneId zone,
            ThreadPoolTaskScheduler taskScheduler) {
        this.orchestrator = orchestrator;
        this.runtimeSettings = runtimeSettings;
        this.zone = zone;
        this.taskScheduler = taskScheduler;
    }

    @PostConstruct
    void start() {
        taskScheduler.setThreadNamePrefix("sysacad-sync-");
        taskScheduler.initialize();
        reschedule();
    }

    @PreDestroy
    void stop() {
        taskScheduler.shutdown();
    }

    @ApplicationModuleListener
    void onSettingChanged(SettingChangedEvent event) {
        if (event.key() == SettingKey.SYSACAD_SYNC_DAY_OF_WEEK || event.key() == SettingKey.SYSACAD_SYNC_TIME) {
            reschedule();
        }
    }

    public void resyncAll() {
        log.info("Cron de sync de SysAcad disparado");
        orchestrator.resyncAll();
    }

    private synchronized void reschedule() {
        if (scheduled != null) {
            scheduled.cancel(false);
        }
        String cron = runtimeSettings.syncCron();
        scheduled = taskScheduler.schedule(this::resyncAll, new CronTrigger(cron, zone));
        log.info("Sync de SysAcad agendado con cron '{}' en {}", cron, zone);
    }
}
