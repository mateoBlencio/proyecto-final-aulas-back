package ar.edu.utn.frc.siga.dashboard.scheduler;

import ar.edu.utn.frc.siga.dashboard.service.DashboardSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Closes each month: the cron runs on the 1st and the startup run recovers months missed while the app was down. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.dashboard.snapshot", name = "enabled", havingValue = "true")
public class DashboardSnapshotScheduler {

    private final DashboardSnapshotService snapshotService;

    @Scheduled(cron = "${siga.dashboard.snapshot.cron}", zone = "America/Argentina/Buenos_Aires")
    public void generateClosedMonths() {
        log.info("Cron de snapshots del tablero disparado");
        snapshotService.generateMissingMonths();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        log.info("Recuperación de snapshots del tablero al arrancar");
        snapshotService.generateMissingMonths();
    }
}
