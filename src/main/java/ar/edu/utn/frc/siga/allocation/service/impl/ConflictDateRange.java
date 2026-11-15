package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.common.util.DateRanges;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
class ConflictDateRange {

    private final AcademicPeriodService academicPeriodService;

    record Range(LocalDate from, LocalDate to) {
    }

    Range resolve(LocalDate from, LocalDate to) {
        return withDefaultTo(DateRanges.defaultFrom(from), to);
    }

    Range resolveFromPeriodStart(LocalDate from, LocalDate to) {
        LocalDate effectiveFrom = from != null ? from : academicPeriodService.findActive().stream()
                .map(AcademicPeriodResponseDto::startDate)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElseGet(LocalDate::now);
        return withDefaultTo(effectiveFrom, to);
    }

    private Range withDefaultTo(LocalDate from, LocalDate to) {
        LocalDate effectiveTo = to != null ? to : academicPeriodService.findActive().stream()
                .map(AcademicPeriodResponseDto::endDate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElseGet(() -> from.plusMonths(6));
        DateRanges.requireNotBefore(effectiveTo, from);
        log.debug("Rango de conflictos de asignación: from={}, to={}", from, effectiveTo);
        return new Range(from, effectiveTo);
    }
}
