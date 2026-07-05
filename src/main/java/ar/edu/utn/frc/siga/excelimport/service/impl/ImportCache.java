package ar.edu.utn.frc.siga.excelimport.service.impl;

import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

class ImportCache {

    private final Map<Integer, Specialty> specialties = new HashMap<>();
    private final Map<String, StudyPlan> studyPlans = new HashMap<>();
    private final Map<String, Subject> subjects = new HashMap<>();
    private final Map<String, AcademicPeriod> periods = new HashMap<>();
    private final Map<String, Commission> commissions = new HashMap<>();
    private final Map<String, SubjectCommission> subjectCommissions = new HashMap<>();
    private final Map<String, Building> buildings = new HashMap<>();
    private final Map<String, Classroom> classrooms = new HashMap<>();

    Specialty getSpecialty(Integer code, Supplier<Specialty> loader) {
        return specialties.computeIfAbsent(code, k -> loader.get());
    }

    StudyPlan getStudyPlan(String key, Supplier<StudyPlan> loader) {
        return studyPlans.computeIfAbsent(key, k -> loader.get());
    }

    Subject getSubject(String key, Supplier<Subject> loader) {
        return subjects.computeIfAbsent(key, k -> loader.get());
    }

    AcademicPeriod getPeriod(String key, Supplier<AcademicPeriod> loader) {
        return periods.computeIfAbsent(key, k -> loader.get());
    }

    Commission getCommission(String key, Supplier<Commission> loader) {
        return commissions.computeIfAbsent(key, k -> loader.get());
    }

    SubjectCommission getSubjectCommission(String key, Supplier<SubjectCommission> loader) {
        return subjectCommissions.computeIfAbsent(key, k -> loader.get());
    }

    Building getBuilding(String key, Supplier<Building> loader) {
        return buildings.computeIfAbsent(key, k -> loader.get());
    }

    Classroom getClassroom(String key, Supplier<Classroom> loader) {
        return classrooms.computeIfAbsent(key, k -> loader.get());
    }
}
