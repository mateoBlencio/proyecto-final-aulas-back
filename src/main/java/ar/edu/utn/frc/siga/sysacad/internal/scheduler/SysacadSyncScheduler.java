package ar.edu.utn.frc.siga.sysacad.internal.scheduler;

import ar.edu.utn.frc.siga.sysacad.internal.service.SysacadSyncOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadSyncScheduler {

    private final SysacadSyncOrchestrator orchestrator;

    @Scheduled(cron = "${siga.sysacad.sync.cron}", zone = "America/Argentina/Buenos_Aires")
    public void resyncAll() {
        log.info("Cron de sync de SysAcad disparado");
        orchestrator.resyncAll();
    }
}
