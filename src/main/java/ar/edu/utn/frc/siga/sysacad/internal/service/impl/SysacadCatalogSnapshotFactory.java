package ar.edu.utn.frc.siga.sysacad.internal.service.impl;

import ar.edu.utn.frc.siga.common.util.Lazy;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.BuildingViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.ClassroomViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.CommissionViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.HolidayViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.ScheduleViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.SpecialtyViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.SubjectViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.mapper.SysacadCatalogMapper;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawCommission;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSubject;
import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadCatalogSnapshotFactory {

    private final BuildingViewFetcher buildingViewFetcher;
    private final ClassroomViewFetcher classroomViewFetcher;
    private final SpecialtyViewFetcher specialtyViewFetcher;
    private final CommissionViewFetcher commissionViewFetcher;
    private final ScheduleViewFetcher scheduleViewFetcher;
    private final SubjectViewFetcher subjectViewFetcher;
    private final HolidayViewFetcher holidayViewFetcher;
    private final SysacadCatalogMapper mapper;
    private final SysacadProperties properties;

    public SysacadCatalogReader newSnapshot() {
        Lazy<List<RawSubject>> subjects = Lazy.of(subjectViewFetcher::fetch);
        Lazy<List<RawCommission>> commissions = Lazy.of(commissionViewFetcher::fetch);
        Lazy<List<RawSchedule>> schedules = Lazy.of(scheduleViewFetcher::fetch);
        SysacadProperties.Sync sync = properties.getSync();
        if (sync.isScoped()) {
            SyncScope scope = new SyncScope(sync.onlyCourseCode(), sync.onlySpecialty(), sync.onlySubject());
            Lazy<List<RawSubject>> allSubjects = subjects;
            subjects = Lazy.of(() -> scope.subjects(allSubjects.get()));
            Lazy<List<RawCommission>> allCommissions = commissions;
            commissions = Lazy.of(() -> scope.commissions(allCommissions.get()));
            Lazy<List<RawSchedule>> allSchedules = schedules;
            schedules = Lazy.of(() -> scope.schedules(allSchedules.get()));
        }
        return new SysacadCatalogSnapshot(
                Lazy.of(buildingViewFetcher::fetch),
                Lazy.of(classroomViewFetcher::fetch),
                Lazy.of(specialtyViewFetcher::fetch),
                commissions,
                schedules,
                subjects,
                Lazy.of(holidayViewFetcher::fetch),
                mapper);
    }
}
