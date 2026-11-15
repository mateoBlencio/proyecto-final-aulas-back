package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.common.util.DateRanges;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
class ConflictDateRange {

    private final AcademicPeriodService academicPeriodService;

    record Range(LocalDate from, LocalDate to) {
    }

    Range resolve(LocalDate from, LocalDate to) {
        List<AcademicPeriodResponseDto> periods = to != null ? List.of() : academicPeriodService.findActive();
        return withDefaultTo(DateRanges.defaultFrom(from), to, periods);
    }

    Range resolveFromPeriodStart(LocalDate from, LocalDate to) {
        List<AcademicPeriodResponseDto> periods = from != null && to != null
                ? List.of()
                : academicPeriodService.findActive();
        LocalDate effectiveFrom = from != null ? from : periods.stream()
                .map(AcademicPeriodResponseDto::startDate)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElseGet(LocalDate::now);
        return withDefaultTo(effectiveFrom, to, periods);
    }

    private Range withDefaultTo(LocalDate from, LocalDate to, List<AcademicPeriodResponseDto> periods) {
        LocalDate effectiveTo = to != null ? to : periods.stream()
                .map(AcademicPeriodResponseDto::endDate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElseGet(() -> from.plusMonths(6));
        DateRanges.requireNotBefore(effectiveTo, from);
        log.debug("Rango de fechas resuelto: from={}, to={}", from, effectiveTo);
        return new Range(from, effectiveTo);
    }
}
