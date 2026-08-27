package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.command.SyncRecurringEventCommand;
import ar.edu.utn.frc.siga.events.service.command.UpsertRecurringEventResult;
import ar.edu.utn.frc.siga.sysacad.api.SysacadAcademicEventDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import ar.edu.utn.frc.siga.sysacad.api.SysacadViewSyncer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class AcademicEventSyncService implements SysacadViewSyncer {

    private final SysacadCatalogReader catalogReader;
    private final CommissionService commissionService;
    private final SubjectCommissionService subjectCommissionService;
    private final AcademicEventService academicEventService;
    private final SysacadSyncStateService syncStateService;

    @Override
    public SysacadView view() {
        return SysacadView.EVENTOS;
    }

    @Override
    public void sync() {
        try {
            Map<String, Optional<CommissionResponseDto>> commissionCache = new HashMap<>();
            Map<SysacadCommissionResolver.LinkKey, Optional<SubjectCommissionResponseDto>> linkCache = new HashMap<>();
            Set<Long> presentEventIds = new HashSet<>();
            int affected = 0;

            for (SysacadAcademicEventDto row : catalogReader.findAcademicEvents()) {
                Optional<CommissionResponseDto> commission =
                        SysacadCommissionResolver.resolveCommission(commissionService, commissionCache, row.courseCode());
                if (commission.isEmpty()) {
                    continue;
                }
                Optional<SubjectCommissionResponseDto> link = SysacadCommissionResolver.resolveLink(
                        subjectCommissionService, linkCache, commission.get().id(), row.subjectCode());
                if (link.isEmpty()) {
                    continue;
                }
                if (row.durationMinutes() == null) {
                    log.warn("DURACION nula para curso={} materia={}: fila de evento salteada",
                            row.courseCode(), row.subjectCode());
                    continue;
                }

                int year = commission.get().academicPeriod().year();
                for (TermType termType : SysacadCommissionResolver.termTypes(
                        row.semester(), row.courseCode(), row.subjectCode())) {
                    SyncRecurringEventCommand cmd = new SyncRecurringEventCommand(
                            link.get().subjectId(),
                            commission.get().id(),
                            row.dayOfWeek(),
                            row.startTime(),
                            row.durationMinutes(),
                            link.get().enrolledCount(),
                            termType.startDate(year),
                            termType.endDate(year));
                    UpsertRecurringEventResult result = academicEventService.syncRecurringEvent(cmd);
                    presentEventIds.add(result.eventId());
                    affected++;
                }
            }

            affected += academicEventService.markRecurringEventsAbsent(presentEventIds);
            syncStateService.recordSuccess(SysacadView.EVENTOS, affected);
            log.info("Sync de Eventos finalizado: {} filas afectadas", affected);
        } catch (RuntimeException e) {
            syncStateService.recordFailure(SysacadView.EVENTOS, e.getMessage());
            throw e;
        }
    }
}
