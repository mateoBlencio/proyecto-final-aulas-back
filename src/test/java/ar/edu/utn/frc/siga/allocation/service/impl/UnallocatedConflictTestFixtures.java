package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.StudyPlanResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

final class UnallocatedConflictTestFixtures {

    private UnallocatedConflictTestFixtures() {
    }

    static SubjectResponseDto subject(String name, String specialtyName) {
        return new SubjectResponseDto(1L, 1, name, "ANUAL",
                new StudyPlanResponseDto(1L, 2023, new SpecialtyResponseDto(1, specialtyName, "ESP"), true),
                true, null);
    }

    static CommissionResponseDto commission(String courseCode) {
        return courseCode == null ? null : new CommissionResponseDto(1L, courseCode, null, null);
    }

    static RecurringEventResponseDto recurring(long id, String subjectName, String specialtyName, String courseCode,
                                               DayOfWeek day, LocalTime start, Integer enrolled) {
        return new RecurringEventResponseDto(id, EventType.RECURRING, enrolled, start, 60, day,
                LocalDate.of(2026, 1, 1), null, subject(subjectName, specialtyName), commission(courseCode));
    }

    static UniqueEventResponseDto unique(long id, String subjectName, String specialtyName, String courseCode,
                                         LocalDate date, LocalTime start, Integer enrolled) {
        return new UniqueEventResponseDto(id, EventType.UNIQUE_EVENT, UniqueEventKind.OTRO, enrolled, start, 60, date, null,
                subject(subjectName, specialtyName), commission(courseCode), false, false, null, null, null);
    }
}
