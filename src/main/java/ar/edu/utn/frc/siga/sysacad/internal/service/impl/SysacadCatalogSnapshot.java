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
import ar.edu.utn.frc.siga.sysacad.api.SysacadHolidayDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSpecialtyDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSubjectCommissionDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSubjectDto;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawBuilding;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawClassroom;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawCommission;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawHoliday;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSpecialty;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSubject;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
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
    private final Lazy<List<RawHoliday>> holidaysRaw;
    private final SysacadCatalogMapper mapper;

    SysacadCatalogSnapshot(Lazy<List<RawBuilding>> buildingsRaw,
            Lazy<List<RawClassroom>> classroomsRaw,
            Lazy<List<RawSpecialty>> specialtiesRaw,
            Lazy<List<RawCommission>> commissionsRaw,
            Lazy<List<RawSchedule>> schedulesRaw,
            Lazy<List<RawSubject>> subjectsRaw,
            Lazy<List<RawHoliday>> holidaysRaw,
            SysacadCatalogMapper mapper) {
        this.buildingsRaw = buildingsRaw;
        this.classroomsRaw = classroomsRaw;
        this.specialtiesRaw = specialtiesRaw;
        this.commissionsRaw = commissionsRaw;
        this.schedulesRaw = schedulesRaw;
        this.subjectsRaw = subjectsRaw;
        this.holidaysRaw = holidaysRaw;
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
        Map<SubjectNaturalKey, SubjectSchedule> scheduleByKey = groupSchedule(schedulesRaw.get());
        return subjectsRaw.get().stream()
                .map(raw -> mapper.toSubject(raw, resolveTerm(scheduleByKey, raw)))
                .toList();
    }

    @Override
    public List<SysacadSubjectCommissionDto> findSubjectCommissions() {
        Map<SubjectCommissionKey, SubjectSchedule> scheduleByKey = new HashMap<>();
        List<SysacadSubjectCommissionDto> rows = new ArrayList<>();
        for (RawSchedule raw : schedulesRaw.get()) {
            SysacadSubjectCommissionDto dto = mapper.toSubjectCommission(raw);
            if (dto == null) {
                continue;
            }
            rows.add(dto);
            SubjectSchedule facts = scheduleByKey.computeIfAbsent(SubjectCommissionKey.of(dto),
                    k -> new SubjectSchedule());
            facts.subjectTerms().add(trimToNull(raw.comisionDictado()));
            facts.commissionSemesters().add(raw.horarioCuatrimestre());
        }
        Map<SubjectCommissionKey, Integer> semesterByKey = new HashMap<>();
        scheduleByKey.forEach((key, facts) -> semesterByKey.put(key, resolveSemester("ComisionDictado",
                "curso=%s especialidad=%s plan=%s materia=%s".formatted(key.courseCode(), key.specialtyCode(),
                        key.studyPlanCode(), key.subjectCode()), facts)));
        return rows.stream().map(dto -> dto.withSemester(semesterByKey.get(SubjectCommissionKey.of(dto)))).toList();
    }

    @Override
    public List<SysacadCommissionDto> findCommissions() {
        List<RawCommission> rows = commissionsRaw.get();
        mapper.warnCourseCodeCollisions(rows.stream().map(RawCommission::curso).toList());
        return rows.stream().map(mapper::toCommission).filter(Objects::nonNull).toList();
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

    @Override
    public List<SysacadHolidayDto> findHolidays() {
        return holidaysRaw.get().stream()
                .map(mapper::toHoliday)
                .filter(Objects::nonNull)
                .toList();
    }

    private static Map<SubjectNaturalKey, SubjectSchedule> groupSchedule(List<RawSchedule> schedule) {
        Map<SubjectNaturalKey, SubjectSchedule> byKey = new HashMap<>();
        for (RawSchedule row : schedule) {
            SubjectNaturalKey key = new SubjectNaturalKey(row.especialidad(), row.plan(), row.materia());
            SubjectSchedule facts = byKey.computeIfAbsent(key, k -> new SubjectSchedule());
            facts.subjectTerms().add(trimToNull(row.materiaDictado()));
            facts.commissionSemesters().add(row.horarioCuatrimestre());
        }
        return byKey;
    }

    private String resolveTerm(Map<SubjectNaturalKey, SubjectSchedule> scheduleByKey, RawSubject raw) {
        SubjectNaturalKey key = new SubjectNaturalKey(raw.especialid(), raw.plan(), raw.materia());
        SubjectSchedule facts = scheduleByKey.get(key);
        if (facts == null) {
            return null;
        }
        String where = "especialidad=%s plan=%s materia=%s".formatted(raw.especialid(), raw.plan(), raw.materia());
        Integer semester = resolveSemester("MateriaDictado", where, facts);
        return semester == null ? null : TermType.fromSemester(semester).map(TermType::getLabel).orElse(null);
    }

    /**
     * Resolves the semester (0 annual, 1, 2) from a dictado code ("A", "1", "2", "C") and the
     * HorarioCuatrimestre values of the rows. Conflicting codes give null. Without a usable code ("C" or
     * empty), the HorarioCuatrimestre values decide.
     */
    private static Integer resolveSemester(String field, String where, SubjectSchedule facts) {
        Set<String> terms = new HashSet<>(facts.subjectTerms());
        terms.remove(null);
        if (terms.size() > 1) {
            log.warn("{} en conflicto para {}: {}", field, where, terms);
            return null;
        }
        String term = terms.isEmpty() ? "" : terms.iterator().next();
        return switch (term) {
            case "A" -> TermType.ANUAL.getSemester();
            case "1" -> TermType.PRIMER_CUATRIMESTRE.getSemester();
            case "2" -> TermType.SEGUNDO_CUATRIMESTRE.getSemester();
            case "C" -> singleSemester(withoutZero(facts.commissionSemesters()));
            default -> semesterFromCommissions(facts.commissionSemesters());
        };
    }

    /**
     * Without a dictado code, HorarioCuatrimestre = 0 means annual and is treated like in events
     * ({@code SysacadCommissionResolver.termTypes}: it covers both semesters). A 0 in any row makes it
     * annual, even when the rest is 1 or 2 (the annual schedule already covers both). Without a 0, a single
     * semester gives that semester; two different ones stay null.
     */
    private static Integer semesterFromCommissions(Set<Integer> semesters) {
        if (semesters.contains(0)) {
            return TermType.ANUAL.getSemester();
        }
        return singleSemester(withoutZero(semesters));
    }

    private static Set<Integer> withoutZero(Set<Integer> semesters) {
        Set<Integer> cuatrimestrales = new HashSet<>(semesters);
        cuatrimestrales.remove(null);
        cuatrimestrales.remove(0);
        return cuatrimestrales;
    }

    private static Integer singleSemester(Set<Integer> cuatrimestrales) {
        if (cuatrimestrales.size() == 1) {
            return TermType.fromSemester(cuatrimestrales.iterator().next()).map(TermType::getSemester).orElse(null);
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record SubjectNaturalKey(Integer especialidad, Integer plan, Integer materia) {
    }

    private record SubjectCommissionKey(String courseCode, Integer specialtyCode, Integer studyPlanCode,
            Integer subjectCode) {
        static SubjectCommissionKey of(SysacadSubjectCommissionDto dto) {
            return new SubjectCommissionKey(dto.courseCode(), dto.specialtyCode(), dto.studyPlanCode(),
                    dto.subjectCode());
        }
    }

    private record SubjectSchedule(Set<String> subjectTerms, Set<Integer> commissionSemesters) {
        SubjectSchedule() {
            this(new HashSet<>(), new HashSet<>());
        }
    }
}
