package ar.edu.utn.frc.siga.audit.internal;

import ar.edu.utn.frc.siga.audit.service.AuditArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.audit.archive", name = "enabled", havingValue = "true")
public class AuditArchiveScheduler {

    private final AuditArchiveService archiveService;

    @Scheduled(cron = "${siga.audit.archive.cron}")
    public void archive() {
        log.info("Cron de archivado de auditoría disparado");
        archiveService.archive(LocalDate.now());
    }
}
