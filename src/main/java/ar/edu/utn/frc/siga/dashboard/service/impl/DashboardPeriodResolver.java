package ar.edu.utn.frc.siga.dashboard.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.dashboard.model.DashboardPeriodType;
import ar.edu.utn.frc.siga.dashboard.model.ResolvedPeriod;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves the dates of the term, academic year or month that contains today. Terms come from the ANUAL period
 * and its recess; each missing date falls back to the defaults of {@link TermType}.
 */
@Component
@RequiredArgsConstructor
public class DashboardPeriodResolver {

    private final AcademicPeriodService academicPeriodService;
    private final Clock clock;

    private record Term(int year, int semester, LocalDate start, LocalDate end) {
    }

    public ResolvedPeriod resolve(DashboardPeriodType type) {
        LocalDate today = LocalDate.now(clock);
        int year = today.getYear();
        return switch (type) {
            case TERM -> term(today, year);
            case ACADEMIC_YEAR -> academicYear(today, year);
            case CURRENT_MONTH -> resolved(type, year, null, today.withDayOfMonth(1),
                    YearMonth.from(today).atEndOfMonth(), today);
        };
    }

    /** The last term that already started: after the end of a term, or in a recess, that term is shown whole. */
    private ResolvedPeriod term(LocalDate today, int year) {
        Optional<AcademicPeriodResponseDto> current = academicPeriodService.findAnnual(year);
        Optional<AcademicPeriodResponseDto> previous = academicPeriodService.findAnnual(year - 1);
        List<Term> candidates = List.of(secondTerm(year - 1, previous), firstTerm(year, current),
                secondTerm(year, current));
        Term chosen = candidates.getFirst();
        for (Term candidate : candidates) {
            if (!candidate.start().isAfter(today)) {
                chosen = candidate;
            }
        }
        return resolved(DashboardPeriodType.TERM, chosen.year(), chosen.semester(), chosen.start(), chosen.end(),
                today);
    }

    private ResolvedPeriod academicYear(LocalDate today, int year) {
        Optional<AcademicPeriodResponseDto> annual = academicPeriodService.findAnnual(year);
        int resolvedYear = year;
        if (annualStart(annual, year).isAfter(today)) {
            resolvedYear = year - 1;
            annual = academicPeriodService.findAnnual(resolvedYear);
        }
        return resolved(DashboardPeriodType.ACADEMIC_YEAR, resolvedYear, TermType.ANUAL.getSemester(),
                annualStart(annual, resolvedYear), annualEnd(annual, resolvedYear), today);
    }

    private static ResolvedPeriod resolved(DashboardPeriodType type, int year, Integer semester, LocalDate start,
                                           LocalDate end, LocalDate today) {
        LocalDate to = today.isBefore(end) ? today : end;
        return new ResolvedPeriod(type, year, semester, start, end, start, to, to.isBefore(end));
    }

    private static LocalDate annualStart(Optional<AcademicPeriodResponseDto> annual, int year) {
        return annual.map(AcademicPeriodResponseDto::startDate).orElseGet(() -> TermType.ANUAL.startDate(year));
    }

    private static LocalDate annualEnd(Optional<AcademicPeriodResponseDto> annual, int year) {
        return annual.map(AcademicPeriodResponseDto::endDate).orElseGet(() -> TermType.ANUAL.endDate(year));
    }

    private static Term firstTerm(int year, Optional<AcademicPeriodResponseDto> annual) {
        LocalDate end = annual.map(AcademicPeriodResponseDto::recessStart).map(d -> d.minusDays(1))
                .orElseGet(() -> TermType.PRIMER_CUATRIMESTRE.endDate(year));
        return new Term(year, 1, annualStart(annual, year), end);
    }

    private static Term secondTerm(int year, Optional<AcademicPeriodResponseDto> annual) {
        LocalDate start = annual.map(AcademicPeriodResponseDto::recessEnd).map(d -> d.plusDays(1))
                .orElseGet(() -> TermType.SEGUNDO_CUATRIMESTRE.startDate(year));
        return new Term(year, 2, start, annualEnd(annual, year));
    }
}
