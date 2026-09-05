package ar.edu.utn.frc.siga.sysacad.internal.service.impl;

import ar.edu.utn.frc.siga.sysacad.internal.mapper.SysacadCatalogMapper;

import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.common.util.Lazy;
import ar.edu.utn.frc.siga.sysacad.api.SysacadAcademicEventDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadAllocationDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadBuildingDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadClassroomDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCommissionDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSpecialtyDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSubjectCommissionDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSubjectDto;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawBuilding;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawClassroom;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawCommission;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSpecialty;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSubject;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
final class SysacadCatalogSnapshot implements SysacadCatalogReader {

    private final Lazy<List<RawBuilding>> buildingsRaw;
    private final Lazy<List<RawClassroom>> classroomsRaw;
    private final Lazy<List<RawSpecialty>> specialtiesRaw;
    private final Lazy<List<RawCommission>> commissionsRaw;
    private final Lazy<List<RawSchedule>> schedulesRaw;
    private final Lazy<List<RawSubject>> subjectsRaw;
    private final SysacadCatalogMapper mapper;

    SysacadCatalogSnapshot(Lazy<List<RawBuilding>> buildingsRaw,
            Lazy<List<RawClassroom>> classroomsRaw,
            Lazy<List<RawSpecialty>> specialtiesRaw,
            Lazy<List<RawCommission>> commissionsRaw,
            Lazy<List<RawSchedule>> schedulesRaw,
            Lazy<List<RawSubject>> subjectsRaw,
            SysacadCatalogMapper mapper) {
        this.buildingsRaw = buildingsRaw;
        this.classroomsRaw = classroomsRaw;
        this.specialtiesRaw = specialtiesRaw;
        this.commissionsRaw = commissionsRaw;
        this.schedulesRaw = schedulesRaw;
        this.subjectsRaw = subjectsRaw;
        this.mapper = mapper;
    }

    @Override
    public List<SysacadBuildingDto> findBuildings() {
        return buildingsRaw.get().stream().map(mapper::toBuilding).toList();
    }

    @Override
    public List<SysacadClassroomDto> findClassrooms() {
        return classroomsRaw.get().stream().map(mapper::toClassroom).toList();
    }

    @Override
    public List<SysacadSpecialtyDto> findSpecialties() {
        return specialtiesRaw.get().stream().map(mapper::toSpecialty).toList();
    }

    @Override
    public List<SysacadSubjectDto> findSubjects() {
        Map<SubjectNaturalKey, Set<Integer>> semestersByKey = groupSemesters(schedulesRaw.get());
        return subjectsRaw.get().stream()
                .map(raw -> mapper.toSubject(raw, resolveTerm(semestersByKey, raw)))
                .toList();
    }

    @Override
    public List<SysacadSubjectCommissionDto> findSubjectCommissions() {
        return schedulesRaw.get().stream().map(mapper::toSubjectCommission).toList();
    }

    @Override
    public List<SysacadCommissionDto> findCommissions() {
        return commissionsRaw.get().stream().map(mapper::toCommission).toList();
    }

    @Override
    public List<SysacadAcademicEventDto> findAcademicEvents() {
        return schedulesRaw.get().stream()
                .map(mapper::toAcademicEvent)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<SysacadAllocationDto> findAllocations() {
        List<RawSchedule> rows = schedulesRaw.get();
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
