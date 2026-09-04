package ar.edu.utn.frc.siga.sysacad.internal.service.impl;

import ar.edu.utn.frc.siga.sysacad.internal.mapper.SysacadCatalogMapper;

import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.sysacad.api.SysacadAcademicEventDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadAllocationDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadBuildingDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadClassroomDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCommissionDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSpecialtyDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSubjectCommissionDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSubjectDto;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSubject;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.BuildingViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.ClassroomViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.CommissionViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.ScheduleViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.SpecialtyViewFetcher;
import ar.edu.utn.frc.siga.sysacad.internal.client.view.SubjectViewFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadCatalogReaderImpl implements SysacadCatalogReader {

    private final BuildingViewFetcher buildingViewFetcher;
    private final ClassroomViewFetcher classroomViewFetcher;
    private final SpecialtyViewFetcher specialtyViewFetcher;
    private final CommissionViewFetcher commissionViewFetcher;
    private final ScheduleViewFetcher scheduleViewFetcher;
    private final SubjectViewFetcher subjectViewFetcher;
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
        Map<SubjectNaturalKey, Set<Integer>> semestersByKey = groupSemesters(scheduleViewFetcher.fetch());
        return subjectViewFetcher.fetch().stream()
                .map(raw -> mapper.toSubject(raw, resolveTerm(semestersByKey, raw)))
                .toList();
    }

    @Override
    public List<SysacadSubjectCommissionDto> findSubjectCommissions() {
        return scheduleViewFetcher.fetch().stream().map(mapper::toSubjectCommission).toList();
    }

    @Override
    public List<SysacadCommissionDto> findCommissions() {
        return commissionViewFetcher.fetch().stream().map(mapper::toCommission).toList();
    }

    @Override
    public List<SysacadAcademicEventDto> findAcademicEvents() {
        return scheduleViewFetcher.fetch().stream()
                .map(mapper::toAcademicEvent)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<SysacadAllocationDto> findAllocations() {
        List<RawSchedule> rows = scheduleViewFetcher.fetch();
        return rows.stream()
                .map(mapper::toAllocation)
                .filter(Objects::nonNull)
                .toList();
    }

    private static Map<SubjectNaturalKey, Set<Integer>> groupSemesters(List<RawSchedule> schedule) {
        Map<SubjectNaturalKey, Set<Integer>> semestersByKey = new HashMap<>();
        for (RawSchedule row : schedule) {
            SubjectNaturalKey key = new SubjectNaturalKey(row.especialidad(), row.plan(), row.materia());
            semestersByKey.computeIfAbsent(key, k -> new HashSet<>()).add(row.horarioCuatrimestre());
        }
        return semestersByKey;
    }

    private String resolveTerm(Map<SubjectNaturalKey, Set<Integer>> semestersByKey, RawSubject raw) {
        SubjectNaturalKey key = new SubjectNaturalKey(raw.especialid(), raw.plan(), raw.materia());
        Set<Integer> semesters = semestersByKey.getOrDefault(key, Set.of());
        if (semesters.isEmpty()) {
            return null;
        }
        if (semesters.size() == 1) {
            return TermType.fromSemester(semesters.iterator().next()).map(TermType::getLabel).orElse(null);
        }
        log.warn("HorarioCuatrimestre en conflicto para especialidad={} plan={} materia={}: {}",
                raw.especialid(), raw.plan(), raw.materia(), semesters);
        return null;
    }

    private record SubjectNaturalKey(Integer especialidad, Integer plan, Integer materia) {
    }
}
