package ar.edu.utn.frc.classroom_allocation.excelimport.service.impl;

import ar.edu.utn.frc.classroom_allocation.career.model.Specialty;
import ar.edu.utn.frc.classroom_allocation.career.model.StudyPlan;
import ar.edu.utn.frc.classroom_allocation.career.model.Subject;
import ar.edu.utn.frc.classroom_allocation.course.model.AcademicPeriod;
import ar.edu.utn.frc.classroom_allocation.course.model.Commission;
import ar.edu.utn.frc.classroom_allocation.course.model.SubjectCommission;
import ar.edu.utn.frc.classroom_allocation.schedule.model.TimeSlot;
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
    private final Map<String, TimeSlot> timeSlots = new HashMap<>();

    <T> T getOrFetch(Map<String, T> cache, String key, Supplier<T> loader) {
        return cache.computeIfAbsent(key, k -> loader.get());
    }

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

    TimeSlot getTimeSlot(String key, Supplier<TimeSlot> loader) {
        return timeSlots.computeIfAbsent(key, k -> loader.get());
    }
}
