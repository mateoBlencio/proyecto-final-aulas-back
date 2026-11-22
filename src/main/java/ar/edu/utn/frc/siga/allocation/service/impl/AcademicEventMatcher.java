package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;

import java.text.Normalizer;
import java.time.DayOfWeek;

final class AcademicEventMatcher {

    private AcademicEventMatcher() {
    }

    static boolean matches(AcademicEventResponseDto event, String q, DayOfWeek dayOfWeek, String specialty) {
        if (dayOfWeek != null && UnallocatedConflictComparator.dayOf(event) != dayOfWeek) {
            return false;
        }
        var subject = event.subject();
        String specialtyName = subject == null ? null : subject.studyPlan().specialty().name();
        if (specialty != null && !specialty.equals(specialtyName)) {
            return false;
        }
        if (q == null || q.isBlank()) {
            return true;
        }
        String needle = fold(q.strip());
        CommissionResponseDto commission = switch (event) {
            case RecurringEventResponseDto r -> r.commission();
            case UniqueEventResponseDto u -> u.commission();
        };
        return fold(subject == null ? null : subject.name()).contains(needle)
                || fold(specialtyName).contains(needle)
                || (commission != null && fold(commission.courseCode()).contains(needle));
    }

    private static String fold(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase();
    }
}
