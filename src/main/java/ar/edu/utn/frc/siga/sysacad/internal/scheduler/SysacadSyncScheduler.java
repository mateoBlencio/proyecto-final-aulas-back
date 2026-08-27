package ar.edu.utn.frc.siga.sysacad.internal.scheduler;

import ar.edu.utn.frc.siga.sysacad.internal.service.SysacadSyncOrchestrator;

import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
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

    @Scheduled(cron = "${siga.sysacad.sync.edificios}")
    public void syncBuildings() {
        log.info("Cron de sync de Edificios disparado");
        orchestrator.sync(SysacadView.EDIFICIOS);
    }

    @Scheduled(cron = "${siga.sysacad.sync.aulas}")
    public void syncClassrooms() {
        log.info("Cron de sync de Aulas disparado");
        orchestrator.sync(SysacadView.AULAS);
    }

    @Scheduled(cron = "${siga.sysacad.sync.especialidades}")
    public void syncSpecialties() {
        log.info("Cron de sync de Especialidades disparado");
        orchestrator.sync(SysacadView.ESPECIALIDADES);
    }

    @Scheduled(cron = "${siga.sysacad.sync.materias}")
    public void syncSubjects() {
        log.info("Cron de sync de Materias disparado");
        orchestrator.sync(SysacadView.MATERIAS);
    }

    @Scheduled(cron = "${siga.sysacad.sync.comisiones}")
    public void syncCommissions() {
        log.info("Cron de sync de Comisiones disparado");
        orchestrator.sync(SysacadView.COMISIONES);
    }
}
