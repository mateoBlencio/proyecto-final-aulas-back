package ar.edu.utn.frc.siga.sysacad.internal.service.impl;

import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawCommission;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSubject;

import java.util.List;

final class SyncScope {

    private final String courseCode;
    private final Integer specialty;
    private final Integer subject;

    SyncScope(String courseCode, Integer specialty, Integer subject) {
        this.courseCode = courseCode.trim();
        this.specialty = specialty;
        this.subject = subject;
    }

    List<RawSubject> subjects(List<RawSubject> rows) {
        return rows.stream().filter(raw -> matches(raw.especialid(), raw.materia())).toList();
    }

    List<RawCommission> commissions(List<RawCommission> rows) {
        return rows.stream()
                .filter(raw -> matches(raw.especialid(), raw.materia()) && sameCourse(raw.curso()))
                .toList();
    }

    List<RawSchedule> schedules(List<RawSchedule> rows) {
        return rows.stream()
                .filter(raw -> matches(raw.especialidad(), raw.materia()) && sameCourse(raw.curso()))
                .toList();
    }

    private boolean matches(Integer rowSpecialty, Integer rowSubject) {
        return specialty.equals(rowSpecialty) && subject.equals(rowSubject);
    }

    private boolean sameCourse(String rowCourse) {
        return rowCourse != null && courseCode.equalsIgnoreCase(rowCourse.trim());
    }
}
