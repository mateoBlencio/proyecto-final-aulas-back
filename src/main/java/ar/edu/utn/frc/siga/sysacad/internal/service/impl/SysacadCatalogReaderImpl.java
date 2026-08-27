package ar.edu.utn.frc.siga.sysacad.internal.service.impl;

import ar.edu.utn.frc.siga.sysacad.internal.mapper.SysacadCatalogMapper;

import ar.edu.utn.frc.siga.sysacad.api.SysacadAcademicEventDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadAllocationDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadBuildingDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadClassroomDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCommissionDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSpecialtyDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSubjectCommissionDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSubjectDto;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.AcademicEventMockViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.BuildingViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.ClassroomViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.CommissionViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.ScheduleViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.SpecialtyViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.SubjectCommissionMockViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.SubjectMockViewFetcher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadCatalogReaderImpl implements SysacadCatalogReader {

    private final BuildingViewFetcher buildingViewFetcher;
    private final ClassroomViewFetcher classroomViewFetcher;
    private final SpecialtyViewFetcher specialtyViewFetcher;
    private final CommissionViewFetcher commissionViewFetcher;
    private final ScheduleViewFetcher scheduleViewFetcher;
    private final Optional<SubjectMockViewFetcher> subjectMockViewFetcher;
    private final Optional<SubjectCommissionMockViewFetcher> subjectCommissionMockViewFetcher;
    private final Optional<AcademicEventMockViewFetcher> academicEventMockViewFetcher;
    private final SysacadCatalogMapper mapper;

    @Override
    public List<SysacadBuildingDto> findBuildings() {
        return buildingViewFetcher.fetch().stream().map(mapper::toBuilding).toList();
    }

    @Override
    public List<SysacadClassroomDto> findClassrooms() {
        return classroomViewFetcher.fetch().stream().map(mapper::toClassroom).toList();
    }

    @Override
    public List<SysacadSpecialtyDto> findSpecialties() {
        return specialtyViewFetcher.fetch().stream().map(mapper::toSpecialty).toList();
    }

    @Override
    public List<SysacadSubjectDto> findSubjects() {
        return subjectMockViewFetcher
                .map(fetcher -> fetcher.fetch().stream().map(mapper::toSubject).toList())
                .orElseGet(List::of);
    }

    @Override
    public List<SysacadSubjectCommissionDto> findSubjectCommissions() {
        return subjectCommissionMockViewFetcher
                .map(fetcher -> fetcher.fetch().stream().map(mapper::toSubjectCommission).toList())
                .orElseGet(List::of);
    }

    @Override
    public List<SysacadCommissionDto> findCommissions() {
        return commissionViewFetcher.fetch().stream().map(mapper::toCommission).toList();
    }

    @Override
    public List<SysacadAcademicEventDto> findAcademicEvents() {
        return academicEventMockViewFetcher
                .map(fetcher -> fetcher.fetch().stream()
                        .map(mapper::toAcademicEvent)
                        .filter(Objects::nonNull)
                        .toList())
                .orElseGet(List::of);
    }

    @Override
    public List<SysacadAllocationDto> findAllocations() {
        return scheduleViewFetcher.fetch().stream()
                .map(mapper::toAllocation)
                .filter(Objects::nonNull)
                .toList();
    }
}
