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

    @Scheduled(cron = "${siga.sysacad.sync.buildings}", zone = "America/Argentina/Buenos_Aires")
    public void syncBuildings() {
        log.info("Cron de sync de Edificios disparado");
        orchestrator.sync(SysacadView.EDIFICIOS);
    }

    @Scheduled(cron = "${siga.sysacad.sync.classrooms}", zone = "America/Argentina/Buenos_Aires")
    public void syncClassrooms() {
        log.info("Cron de sync de Aulas disparado");
        orchestrator.sync(SysacadView.AULAS);
    }

    @Scheduled(cron = "${siga.sysacad.sync.specialties}", zone = "America/Argentina/Buenos_Aires")
    public void syncSpecialties() {
        log.info("Cron de sync de Especialidades disparado");
        orchestrator.sync(SysacadView.ESPECIALIDADES);
    }

    @Scheduled(cron = "${siga.sysacad.sync.subjects}", zone = "America/Argentina/Buenos_Aires")
    public void syncSubjects() {
        log.info("Cron de sync de Materias disparado");
        orchestrator.sync(SysacadView.MATERIAS);
    }

    @Scheduled(cron = "${siga.sysacad.sync.commissions}", zone = "America/Argentina/Buenos_Aires")
    public void syncCommissions() {
        log.info("Cron de sync de Comisiones disparado");
        orchestrator.sync(SysacadView.COMISIONES);
    }

    @Scheduled(cron = "${siga.sysacad.sync.events}", zone = "America/Argentina/Buenos_Aires")
    public void syncAcademicEvents() {
        log.info("Cron de sync de Eventos disparado");
        orchestrator.sync(SysacadView.EVENTOS);
    }

    @Scheduled(cron = "${siga.sysacad.sync.allocations}", zone = "America/Argentina/Buenos_Aires")
    public void syncAllocations() {
        log.info("Cron de sync de Asignaciones disparado");
        orchestrator.sync(SysacadView.ASIGNACIONES);
    }

    @Scheduled(cron = "${siga.sysacad.sync.holidays}", zone = "America/Argentina/Buenos_Aires")
    public void syncHolidays() {
        log.info("Cron de sync de Feriados disparado");
        orchestrator.sync(SysacadView.FERIADOS);
    }
}
