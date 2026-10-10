package ar.edu.utn.frc.siga.dashboard.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.common.exception.InvalidSelectionException;
import ar.edu.utn.frc.siga.dashboard.exception.PeriodNotStartedException;
import ar.edu.utn.frc.siga.dashboard.model.DashboardPeriodType;
import ar.edu.utn.frc.siga.dashboard.model.DateWindow;
import ar.edu.utn.frc.siga.dashboard.model.ResolvedPeriod;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves the dates of a term, academic year or run of academic years. Terms come from the ANUAL period and its
 * recess; each missing date falls back to the defaults of {@link TermType}.
 */
@Component
@RequiredArgsConstructor
public class DashboardPeriodResolver {

    private static final int MAX_LAST_YEARS = 10;

    private final AcademicPeriodService academicPeriodService;
    private final Clock clock;

    private record Term(int year, int semester, LocalDate start, LocalDate end) {
    }

    private record Annual(int year, LocalDate start, LocalDate end) {
    }

    /** Parameters that do not apply to {@code type} are ignored. */
    public ResolvedPeriod resolve(DashboardPeriodType type, Integer year, Integer semester, Integer years) {
        LocalDate today = LocalDate.now(clock);
        return switch (type) {
            case TERM -> term(today, year, semester);
            case ACADEMIC_YEAR -> academicYear(today, year);
            case LAST_YEARS -> lastYears(today, years);
        };
    }

    private ResolvedPeriod term(LocalDate today, Integer year, Integer semester) {
        if ((year == null) != (semester == null)) {
            throw new InvalidSelectionException("year y semester se piden juntos o no se manda ninguno");
        }
        if (year == null) {
            return currentTerm(today);
        }
        if (semester != 1 && semester != 2) {
            throw new InvalidSelectionException("semester debe ser 1 o 2");
        }
        DashboardDateLimits.requireWithinRange(year, clock);
        Optional<AcademicPeriodResponseDto> annual = academicPeriodService.findAnnual(year);
        Term term = semester == 1 ? firstTerm(year, annual) : secondTerm(year, annual);
        if (term.start().isAfter(today)) {
            throw new PeriodNotStartedException("cuatrimestre " + semester + " de " + year, term.start());
        }
        return termPeriod(term, today);
    }

    /** The last term that already started: after the end of a term, or in a recess, that term is shown whole. */
    private ResolvedPeriod currentTerm(LocalDate today) {
        int year = today.getYear();
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
        return termPeriod(chosen, today);
    }

    private static ResolvedPeriod termPeriod(Term term, LocalDate today) {
        return single(DashboardPeriodType.TERM, term.year(), term.semester(), term.start(), term.end(), today);
    }

    private ResolvedPeriod academicYear(LocalDate today, Integer year) {
        Annual annual;
        if (year == null) {
            annual = currentAnnual(today);
        } else {
            DashboardDateLimits.requireWithinRange(year, clock);
            annual = annual(year);
            if (annual.start().isAfter(today)) {
                throw new PeriodNotStartedException("año académico " + year, annual.start());
            }
        }
        return single(DashboardPeriodType.ACADEMIC_YEAR, annual.year(), TermType.ANUAL.getSemester(), annual.start(),
                annual.end(), today);
    }

    private ResolvedPeriod lastYears(LocalDate today, Integer years) {
        if (years == null || years < 1 || years > MAX_LAST_YEARS) {
            throw new InvalidSelectionException("years es obligatorio y va de 1 a " + MAX_LAST_YEARS);
        }
        int anchor = currentAnnual(today).year();
        List<Annual> annuals = new ArrayList<>();
        for (int y = anchor - years + 1; y <= anchor; y++) {
            annuals.add(annual(y));
        }
        List<DateWindow> windows = annuals.stream()
                .map(a -> new DateWindow(a.start(), a.end().isBefore(today) ? a.end() : today))
                .toList();
        LocalDate start = annuals.getFirst().start();
        LocalDate end = annuals.getLast().end();
        LocalDate to = today.isBefore(end) ? today : end;
        return new ResolvedPeriod(DashboardPeriodType.LAST_YEARS, null, null, years, start, end, start, to,
                to.isBefore(end), windows);
    }

    /** The academic year in progress, or the last one that started. */
    private Annual currentAnnual(LocalDate today) {
        Annual annual = annual(today.getYear());
        return annual.start().isAfter(today) ? annual(today.getYear() - 1) : annual;
    }

    private Annual annual(int year) {
        Optional<AcademicPeriodResponseDto> annual = academicPeriodService.findAnnual(year);
        return new Annual(year, annualStart(annual, year), annualEnd(annual, year));
    }

    private static ResolvedPeriod single(DashboardPeriodType type, int year, Integer semester, LocalDate start,
                                         LocalDate end, LocalDate today) {
        LocalDate to = today.isBefore(end) ? today : end;
        return new ResolvedPeriod(type, year, semester, null, start, end, start, to, to.isBefore(end),
                List.of(new DateWindow(start, to)));
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
