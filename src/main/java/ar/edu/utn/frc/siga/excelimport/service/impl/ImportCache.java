package ar.edu.utn.frc.siga.excelimport.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.StudyPlanResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Cache de deduplicación por fila de Excel: evita repetir la búsqueda de la misma
 * entidad ya resuelta en filas anteriores. Cachea DTOs, no entidades: las fachadas
 * {@code api} de {@code academic} y {@code space} solo devuelven DTOs.
 */
class ImportCache {

    private final Map<Integer, SpecialtyResponseDto> specialties = new HashMap<>();
    private final Map<String, StudyPlanResponseDto> studyPlans = new HashMap<>();
    private final Map<String, SubjectResponseDto> subjects = new HashMap<>();
    private final Map<String, AcademicPeriodResponseDto> periods = new HashMap<>();
    private final Map<String, CommissionResponseDto> commissions = new HashMap<>();
    private final Map<String, SubjectCommissionResponseDto> subjectCommissions = new HashMap<>();
    private final Map<String, BuildingResponseDto> buildings = new HashMap<>();
    private final Map<String, ClassroomResponseDto> classrooms = new HashMap<>();

    SpecialtyResponseDto getSpecialty(Integer code, Supplier<SpecialtyResponseDto> loader) {
        return specialties.computeIfAbsent(code, k -> loader.get());
    }

    StudyPlanResponseDto getStudyPlan(String key, Supplier<StudyPlanResponseDto> loader) {
        return studyPlans.computeIfAbsent(key, k -> loader.get());
    }

    SubjectResponseDto getSubject(String key, Supplier<SubjectResponseDto> loader) {
        return subjects.computeIfAbsent(key, k -> loader.get());
    }

    AcademicPeriodResponseDto getPeriod(String key, Supplier<AcademicPeriodResponseDto> loader) {
        return periods.computeIfAbsent(key, k -> loader.get());
    }

    CommissionResponseDto getCommission(String key, Supplier<CommissionResponseDto> loader) {
        return commissions.computeIfAbsent(key, k -> loader.get());
    }

    SubjectCommissionResponseDto getSubjectCommission(String key, Supplier<SubjectCommissionResponseDto> loader) {
        return subjectCommissions.computeIfAbsent(key, k -> loader.get());
    }

    BuildingResponseDto getBuilding(String key, Supplier<BuildingResponseDto> loader) {
        return buildings.computeIfAbsent(key, k -> loader.get());
    }

    ClassroomResponseDto getClassroom(String key, Supplier<ClassroomResponseDto> loader) {
        return classrooms.computeIfAbsent(key, k -> loader.get());
    }
}
