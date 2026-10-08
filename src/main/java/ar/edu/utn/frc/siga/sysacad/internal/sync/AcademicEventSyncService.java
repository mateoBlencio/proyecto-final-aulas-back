package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.audit.AuditOperation;
import ar.edu.utn.frc.siga.audit.AuditOperations;
import ar.edu.utn.frc.siga.common.util.Plurals;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class AcademicEventSyncService implements SysacadViewSyncer {

    private final CommissionService commissionService;
    private final SubjectCommissionService subjectCommissionService;
    private final AcademicEventService academicEventService;
    private final SysacadSyncStateService syncStateService;

    @Override
    public SysacadView view() {
        return SysacadView.EVENTOS;
    }

    @Override
    @AuditOperation("Sincronización de eventos desde SysAcad")
    public void sync(SysacadCatalogReader catalog) {
        ViewSyncRunner.run(syncStateService, SysacadView.EVENTOS, "Eventos", log, () -> doSync(catalog));
    }

    private int doSync(SysacadCatalogReader catalog) {
        SysacadCommissionResolver resolver = new SysacadCommissionResolver(commissionService, subjectCommissionService);
        List<SyncRecurringEventCommand> commands = new ArrayList<>();

        for (SysacadAcademicEventDto row : catalog.findAcademicEvents()) {
            Optional<SysacadCommissionResolver.ResolvedLink> resolved =
                    resolver.resolve(row.courseCode(), row.subjectCode());
            if (resolved.isEmpty()) {
                continue;
            }
            CommissionResponseDto commission = resolved.get().commission();
            SubjectCommissionResponseDto link = resolved.get().link();
            if (row.durationMinutes() == null) {
                log.warn("DURACION nula para curso={} materia={}: fila de evento salteada",
                        row.courseCode(), row.subjectCode());
                continue;
            }

            int year = commission.academicPeriod().year();
            for (TermType termType : SysacadCommissionResolver.termTypes(
                    row.semester(), row.courseCode(), row.subjectCode())) {
                commands.add(new SyncRecurringEventCommand(
                        link.subjectId(),
                        commission.id(),
                        row.dayOfWeek(),
                        row.startTime(),
                        row.durationMinutes(),
                        link.enrolledCount(),
                        termType.startDate(year),
                        termType.endDate(year)));
            }
        }

        List<UpsertRecurringEventResult> results = academicEventService.syncRecurringEvents(commands);
        Set<Long> presentEventIds = results.stream()
                .map(UpsertRecurringEventResult::eventId)
                .collect(Collectors.toSet());
        long created = results.stream().filter(UpsertRecurringEventResult::created).count();
        long updated = results.stream().filter(UpsertRecurringEventResult::updated).count();
        int absent = academicEventService.markRecurringEventsAbsent(presentEventIds);

        AuditOperations.describe("Sincronización de eventos desde SysAcad: " + Plurals.count(created, "alta", "altas")
                + ", " + Plurals.count(updated, "cambio", "cambios") + ", " + Plurals.count(absent, "baja", "bajas"));
        return presentEventIds.size() + absent;
    }
}
