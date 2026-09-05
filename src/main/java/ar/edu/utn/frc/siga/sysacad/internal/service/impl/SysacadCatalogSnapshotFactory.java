package ar.edu.utn.frc.siga.sysacad.internal.service.impl;

import ar.edu.utn.frc.siga.common.util.Lazy;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.BuildingViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.ClassroomViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.CommissionViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.ScheduleViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.SpecialtyViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.SubjectViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.mapper.SysacadCatalogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

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
    private final SysacadCatalogMapper mapper;

    public SysacadCatalogReader newSnapshot() {
        return new SysacadCatalogSnapshot(
                Lazy.of(buildingViewFetcher::fetch),
                Lazy.of(classroomViewFetcher::fetch),
                Lazy.of(specialtyViewFetcher::fetch),
                Lazy.of(commissionViewFetcher::fetch),
                Lazy.of(scheduleViewFetcher::fetch),
                Lazy.of(subjectViewFetcher::fetch),
                mapper);
    }
}
