package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.command.CommissionSyncCommand;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCommissionDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSubjectCommissionDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import ar.edu.utn.frc.siga.sysacad.api.SysacadViewSyncer;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class CommissionSyncService implements SysacadViewSyncer {

    private final SysacadCatalogReader catalogReader;
    private final CommissionService commissionService;
    private final SysacadSyncStateService syncStateService;

    @Override
    public SysacadView view() {
        return SysacadView.COMISIONES;
    }

    @Override
    public void sync() {
        ViewSyncRunner.run(syncStateService, SysacadView.COMISIONES, "Comisiones", log, () -> {
            Map<EnrollmentKey, SysacadSubjectCommissionDto> enrollments =
                    Maps.byId(catalogReader.findSubjectCommissions(), EnrollmentKey::of);
            List<CommissionSyncCommand> commands = catalogReader.findCommissions().stream()
                    .map(row -> toCommand(row, enrollments))
                    .toList();
            return commissionService.syncCommissions(commands);
        });
    }

    private static CommissionSyncCommand toCommand(SysacadCommissionDto row,
            Map<EnrollmentKey, SysacadSubjectCommissionDto> enrollments) {
        SysacadSubjectCommissionDto enrollment = enrollments.get(new EnrollmentKey(row.courseCode(), row.subjectCode()));
        Integer enrolledCount = enrollment == null ? null : enrollment.enrolledCount();
        return new CommissionSyncCommand(row.courseCode(), row.specialtyCode(), row.studyPlanCode(),
                row.subjectCode(), row.academicYear(), enrolledCount);
    }

    private record EnrollmentKey(String courseCode, Integer subjectCode) {
        static EnrollmentKey of(SysacadSubjectCommissionDto dto) {
            return new EnrollmentKey(dto.courseCode(), dto.subjectCode());
        }
    }
}
