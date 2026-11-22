package ar.edu.utn.frc.siga.dashboard.service.impl;

import static ar.edu.utn.frc.siga.dashboard.model.DashboardPeriodType.ACADEMIC_YEAR;
import static ar.edu.utn.frc.siga.dashboard.model.DashboardPeriodType.LAST_YEARS;
import static ar.edu.utn.frc.siga.dashboard.model.DashboardPeriodType.TERM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.common.exception.InvalidDateRangeException;
import ar.edu.utn.frc.siga.common.exception.InvalidSelectionException;
import ar.edu.utn.frc.siga.dashboard.exception.PeriodNotStartedException;
import ar.edu.utn.frc.siga.dashboard.model.DashboardPeriodType;
import ar.edu.utn.frc.siga.dashboard.model.DateWindow;
import ar.edu.utn.frc.siga.dashboard.model.ResolvedPeriod;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DashboardPeriodResolver.resolve")
class DashboardPeriodResolverTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private AcademicPeriodService academicPeriodService;

    @BeforeEach
    void setUp() {
        academicPeriodService = mock(AcademicPeriodService.class);
        when(academicPeriodService.findAnnual(anyInt())).thenReturn(Optional.empty());
    }

    private static LocalDate d(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    private static AcademicPeriodResponseDto annual(int year, LocalDate start, LocalDate end, LocalDate recessStart,
                                                    LocalDate recessEnd) {
        return new AcademicPeriodResponseDto(1L, year, 0, start, end, recessStart, recessEnd);
    }

    /** 2026 reference year: 09/03 to 28/11 with recess 06/07 to 18/07. */
    private void annual2026() {
        when(academicPeriodService.findAnnual(2026)).thenReturn(Optional.of(
                annual(2026, d(2026, 3, 9), d(2026, 11, 28), d(2026, 7, 6), d(2026, 7, 18))));
    }

    private void annual2026WithoutRecess() {
        when(academicPeriodService.findAnnual(2026)).thenReturn(Optional.of(
                annual(2026, d(2026, 3, 9), d(2026, 11, 28), null, null)));
    }

    private void annual2027() {
        when(academicPeriodService.findAnnual(2027)).thenReturn(Optional.of(
                annual(2027, d(2027, 3, 8), d(2027, 11, 27), null, null)));
    }

    /** 23:30 local time: the UTC date is already the next day, so a clock read in UTC would give a wrong day. */
    private ResolvedPeriod resolve(LocalDate today, DashboardPeriodType type) {
        return resolve(today, type, null, null, null);
    }

    private ResolvedPeriod resolve(LocalDate today, DashboardPeriodType type, Integer year, Integer semester,
                                   Integer years) {
        Clock clock = Clock.fixed(today.atTime(23, 30).atZone(ZONE).toInstant(), ZONE);
        return new DashboardPeriodResolver(academicPeriodService, clock).resolve(type, year, semester, years);
    }

    private static ResolvedPeriod expected(DashboardPeriodType type, int year, Integer semester, LocalDate start,
                                           LocalDate end, LocalDate to, boolean partial) {
        return new ResolvedPeriod(type, year, semester, null, start, end, start, to, partial,
                List.of(new DateWindow(start, to)));
    }

    @Test
    @DisplayName("TERM inside the first term: from its start to today, partial")
    void termInsideFirstTerm() {
        annual2026();

        assertThat(resolve(d(2026, 5, 15), TERM)).isEqualTo(expected(TERM, 2026, 1,
                d(2026, 3, 9), d(2026, 7, 5), d(2026, 5, 15), true));
    }

    @Test
    @DisplayName("TERM on the first day of the first term")
    void termOnFirstDay() {
        annual2026();

        assertThat(resolve(d(2026, 3, 9), TERM)).isEqualTo(expected(TERM, 2026, 1,
                d(2026, 3, 9), d(2026, 7, 5), d(2026, 3, 9), true));
    }

    @Test
    @DisplayName("TERM the day before the first term starts shows the second term of the previous year")
    void termDayBeforeFirstTerm() {
        annual2026();

        assertThat(resolve(d(2026, 3, 8), TERM)).isEqualTo(expected(TERM, 2025, 2,
                d(2025, 8, 1), d(2025, 11, 30), d(2025, 11, 30), false));
    }

    @Test
    @DisplayName("TERM on the last day of the first term is complete, not partial")
    void termOnLastDayOfFirstTerm() {
        annual2026();

        assertThat(resolve(d(2026, 7, 5), TERM)).isEqualTo(expected(TERM, 2026, 1,
                d(2026, 3, 9), d(2026, 7, 5), d(2026, 7, 5), false));
    }

    @Test
    @DisplayName("TERM in the recess shows the first term whole, from the first to the last day of the recess")
    void termInRecess() {
        annual2026();

        ResolvedPeriod whole = expected(TERM, 2026, 1, d(2026, 3, 9), d(2026, 7, 5), d(2026, 7, 5), false);
        assertThat(resolve(d(2026, 7, 6), TERM)).isEqualTo(whole);
        assertThat(resolve(d(2026, 7, 10), TERM)).isEqualTo(whole);
        assertThat(resolve(d(2026, 7, 18), TERM)).isEqualTo(whole);
    }

    @Test
    @DisplayName("TERM the day after the recess starts the second term")
    void termFirstDayOfSecondTerm() {
        annual2026();

        assertThat(resolve(d(2026, 7, 19), TERM)).isEqualTo(expected(TERM, 2026, 2,
                d(2026, 7, 19), d(2026, 11, 28), d(2026, 7, 19), true));
    }

    @Test
    @DisplayName("TERM inside the second term")
    void termInsideSecondTerm() {
        annual2026();

        assertThat(resolve(d(2026, 10, 10), TERM)).isEqualTo(expected(TERM, 2026, 2,
                d(2026, 7, 19), d(2026, 11, 28), d(2026, 10, 10), true));
    }

    @Test
    @DisplayName("TERM on the last day of the year's ANUAL is complete; the day after still shows the same term")
    void termAroundTheEndOfTheSecondTerm() {
        annual2026();

        ResolvedPeriod whole = expected(TERM, 2026, 2, d(2026, 7, 19), d(2026, 11, 28), d(2026, 11, 28), false);
        assertThat(resolve(d(2026, 11, 28), TERM)).isEqualTo(whole);
        assertThat(resolve(d(2026, 11, 29), TERM)).isEqualTo(whole);
    }

    @Test
    @DisplayName("TERM in December and on December 31 shows the second term of the year whole")
    void termInDecember() {
        annual2026();

        ResolvedPeriod whole = expected(TERM, 2026, 2, d(2026, 7, 19), d(2026, 11, 28), d(2026, 11, 28), false);
        assertThat(resolve(d(2026, 12, 10), TERM)).isEqualTo(whole);
        assertThat(resolve(d(2026, 12, 31), TERM)).isEqualTo(whole);
    }

    @Test
    @DisplayName("TERM in January and February shows the second term of the previous year whole")
    void termInJanuaryAndFebruary() {
        annual2026();
        annual2027();

        ResolvedPeriod whole = expected(TERM, 2026, 2, d(2026, 7, 19), d(2026, 11, 28), d(2026, 11, 28), false);
        assertThat(resolve(d(2027, 1, 1), TERM)).isEqualTo(whole);
        assertThat(resolve(d(2027, 2, 10), TERM)).isEqualTo(whole);
        assertThat(resolve(d(2027, 3, 7), TERM)).isEqualTo(whole);
    }

    @Test
    @DisplayName("TERM on the day the next year's ANUAL starts moves to its first term")
    void termFirstDayOfNextYear() {
        annual2026();
        annual2027();

        assertThat(resolve(d(2027, 3, 8), TERM)).isEqualTo(expected(TERM, 2027, 1,
                d(2027, 3, 8), d(2027, 7, 31), d(2027, 3, 8), true));
    }

    @Test
    @DisplayName("without recess, the first term ends on July 31 and the second starts on August 1")
    void withoutRecessTermsSplitOnTermTypeDates() {
        annual2026WithoutRecess();

        assertThat(resolve(d(2026, 7, 10), TERM)).isEqualTo(expected(TERM, 2026, 1,
                d(2026, 3, 9), d(2026, 7, 31), d(2026, 7, 10), true));
        assertThat(resolve(d(2026, 7, 31), TERM)).isEqualTo(expected(TERM, 2026, 1,
                d(2026, 3, 9), d(2026, 7, 31), d(2026, 7, 31), false));
        assertThat(resolve(d(2026, 8, 1), TERM)).isEqualTo(expected(TERM, 2026, 2,
                d(2026, 8, 1), d(2026, 11, 28), d(2026, 8, 1), true));
        assertThat(resolve(d(2026, 8, 3), TERM)).isEqualTo(expected(TERM, 2026, 2,
                d(2026, 8, 1), d(2026, 11, 28), d(2026, 8, 3), true));
    }

    @Test
    @DisplayName("with only one recess date loaded, the other limit falls back to the TermType date")
    void halfLoadedRecess() {
        when(academicPeriodService.findAnnual(2026)).thenReturn(Optional.of(
                annual(2026, d(2026, 3, 9), d(2026, 11, 28), d(2026, 7, 6), null)));

        assertThat(resolve(d(2026, 7, 10), TERM)).isEqualTo(expected(TERM, 2026, 1,
                d(2026, 3, 9), d(2026, 7, 5), d(2026, 7, 5), false));
        assertThat(resolve(d(2026, 8, 1), TERM)).isEqualTo(expected(TERM, 2026, 2,
                d(2026, 8, 1), d(2026, 11, 28), d(2026, 8, 1), true));
    }

    @Test
    @DisplayName("without an ANUAL row, TERM uses the TermType dates: March 1 to July 31")
    void withoutAnnualTerm() {
        assertThat(resolve(d(2026, 5, 15), TERM)).isEqualTo(expected(TERM, 2026, 1,
                d(2026, 3, 1), d(2026, 7, 31), d(2026, 5, 15), true));
        assertThat(resolve(d(2026, 10, 10), TERM)).isEqualTo(expected(TERM, 2026, 2,
                d(2026, 8, 1), d(2026, 11, 30), d(2026, 10, 10), true));
    }

    @Test
    @DisplayName("without an ANUAL row, February shows the second term of the previous year with TermType dates")
    void withoutAnnualTermInFebruary() {
        assertThat(resolve(d(2026, 2, 10), TERM)).isEqualTo(expected(TERM, 2025, 2,
                d(2025, 8, 1), d(2025, 11, 30), d(2025, 11, 30), false));
    }

    @Test
    @DisplayName("ANUAL with a null startDate takes the start from TermType.ANUAL")
    void annualWithNullStartDate() {
        when(academicPeriodService.findAnnual(2026)).thenReturn(Optional.of(
                annual(2026, null, d(2026, 11, 28), d(2026, 7, 6), d(2026, 7, 18))));

        assertThat(resolve(d(2026, 5, 15), TERM)).isEqualTo(expected(TERM, 2026, 1,
                d(2026, 3, 1), d(2026, 7, 5), d(2026, 5, 15), true));
    }

    @Test
    @DisplayName("ANUAL with a null endDate takes the end from TermType.ANUAL")
    void annualWithNullEndDate() {
        when(academicPeriodService.findAnnual(2026)).thenReturn(Optional.of(
                annual(2026, d(2026, 3, 9), null, d(2026, 7, 6), d(2026, 7, 18))));

        assertThat(resolve(d(2026, 9, 1), TERM)).isEqualTo(expected(TERM, 2026, 2,
                d(2026, 7, 19), d(2026, 11, 30), d(2026, 9, 1), true));
    }

    @Test
    @DisplayName("an explicit past term comes whole and not partial; the current one is partial")
    void explicitTerm() {
        annual2026();

        assertThat(resolve(d(2026, 10, 10), TERM, 2026, 1, null)).isEqualTo(expected(TERM, 2026, 1,
                d(2026, 3, 9), d(2026, 7, 5), d(2026, 7, 5), false));
        assertThat(resolve(d(2026, 10, 10), TERM, 2026, 2, null)).isEqualTo(expected(TERM, 2026, 2,
                d(2026, 7, 19), d(2026, 11, 28), d(2026, 10, 10), true));
        assertThat(resolve(d(2026, 10, 10), TERM, 2025, 2, null)).isEqualTo(expected(TERM, 2025, 2,
                d(2025, 8, 1), d(2025, 11, 30), d(2025, 11, 30), false));
    }

    @Test
    @DisplayName("an explicit term that starts today resolves; one that starts tomorrow answers PeriodNotStarted")
    void explicitTermNotStarted() {
        annual2026();

        assertThat(resolve(d(2026, 7, 19), TERM, 2026, 2, null)).isEqualTo(expected(TERM, 2026, 2,
                d(2026, 7, 19), d(2026, 11, 28), d(2026, 7, 19), true));
        assertThatThrownBy(() -> resolve(d(2026, 7, 18), TERM, 2026, 2, null))
                .isInstanceOf(PeriodNotStartedException.class);
        assertThatThrownBy(() -> resolve(d(2026, 10, 10), TERM, 2027, 1, null))
                .isInstanceOf(PeriodNotStartedException.class);
    }

    @Test
    @DisplayName("year without semester, semester without year, semester 0 or 3 answer an invalid selection")
    void invalidTermSelection() {
        annual2026();

        assertThatThrownBy(() -> resolve(d(2026, 10, 10), TERM, 2026, null, null))
                .isInstanceOf(InvalidSelectionException.class);
        assertThatThrownBy(() -> resolve(d(2026, 10, 10), TERM, null, 1, null))
                .isInstanceOf(InvalidSelectionException.class);
        assertThatThrownBy(() -> resolve(d(2026, 10, 10), TERM, 2026, 0, null))
                .isInstanceOf(InvalidSelectionException.class);
        assertThatThrownBy(() -> resolve(d(2026, 10, 10), TERM, 2026, 3, null))
                .isInstanceOf(InvalidSelectionException.class);
    }

    @Test
    @DisplayName("the year range is today +-10: the limits pass the range check, one more year away answers an invalid range")
    void yearRange() {
        assertThat(resolve(d(2026, 10, 10), TERM, 2016, 1, null).year()).isEqualTo(2016);
        assertThatThrownBy(() -> resolve(d(2026, 10, 10), TERM, 2015, 1, null))
                .isInstanceOf(InvalidDateRangeException.class);
        assertThatThrownBy(() -> resolve(d(2026, 10, 10), TERM, 2036, 1, null))
                .isInstanceOf(PeriodNotStartedException.class);
        assertThatThrownBy(() -> resolve(d(2026, 10, 10), ACADEMIC_YEAR, 2015, null, null))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    @DisplayName("ACADEMIC_YEAR without year is the ANUAL in progress or the last one that started")
    void academicYearDefault() {
        annual2026();
        annual2027();

        assertThat(resolve(d(2026, 7, 10), ACADEMIC_YEAR)).isEqualTo(expected(ACADEMIC_YEAR, 2026, 0,
                d(2026, 3, 9), d(2026, 11, 28), d(2026, 7, 10), true));
        assertThat(resolve(d(2026, 12, 10), ACADEMIC_YEAR)).isEqualTo(expected(ACADEMIC_YEAR, 2026, 0,
                d(2026, 3, 9), d(2026, 11, 28), d(2026, 11, 28), false));
        assertThat(resolve(d(2027, 3, 7), ACADEMIC_YEAR)).isEqualTo(expected(ACADEMIC_YEAR, 2026, 0,
                d(2026, 3, 9), d(2026, 11, 28), d(2026, 11, 28), false));
        assertThat(resolve(d(2027, 3, 8), ACADEMIC_YEAR)).isEqualTo(expected(ACADEMIC_YEAR, 2027, 0,
                d(2027, 3, 8), d(2027, 11, 27), d(2027, 3, 8), true));
    }

    @Test
    @DisplayName("ACADEMIC_YEAR with an explicit year: past is whole, future answers PeriodNotStarted, no row uses TermType")
    void academicYearExplicit() {
        annual2026();

        assertThat(resolve(d(2027, 5, 1), ACADEMIC_YEAR, 2026, null, null)).isEqualTo(expected(ACADEMIC_YEAR, 2026, 0,
                d(2026, 3, 9), d(2026, 11, 28), d(2026, 11, 28), false));
        assertThat(resolve(d(2027, 5, 1), ACADEMIC_YEAR, 2025, null, null)).isEqualTo(expected(ACADEMIC_YEAR, 2025, 0,
                d(2025, 3, 1), d(2025, 11, 30), d(2025, 11, 30), false));
        assertThatThrownBy(() -> resolve(d(2026, 10, 10), ACADEMIC_YEAR, 2027, null, null))
                .isInstanceOf(PeriodNotStartedException.class);
    }

    @Test
    @DisplayName("ACADEMIC_YEAR with a null startDate or endDate takes that limit from TermType.ANUAL")
    void academicYearWithNullLimits() {
        when(academicPeriodService.findAnnual(2026)).thenReturn(Optional.of(
                annual(2026, null, null, null, null)));

        assertThat(resolve(d(2026, 12, 10), ACADEMIC_YEAR)).isEqualTo(expected(ACADEMIC_YEAR, 2026, 0,
                d(2026, 3, 1), d(2026, 11, 30), d(2026, 11, 30), false));
    }

    @Test
    @DisplayName("LAST_YEARS=2 in progress: previous ANUAL whole and current ANUAL up to today, as two windows")
    void lastYearsInProgress() {
        annual2026();

        assertThat(resolve(d(2026, 10, 10), LAST_YEARS, null, null, 2)).isEqualTo(new ResolvedPeriod(LAST_YEARS,
                null, null, 2, d(2025, 3, 1), d(2026, 11, 28), d(2025, 3, 1), d(2026, 10, 10), true,
                List.of(new DateWindow(d(2025, 3, 1), d(2025, 11, 30)),
                        new DateWindow(d(2026, 3, 9), d(2026, 10, 10)))));
    }

    @Test
    @DisplayName("LAST_YEARS between December and February ends at the last ANUAL that started, not partial")
    void lastYearsBetweenYears() {
        annual2026();
        annual2027();

        assertThat(resolve(d(2027, 2, 10), LAST_YEARS, null, null, 2)).isEqualTo(new ResolvedPeriod(LAST_YEARS,
                null, null, 2, d(2025, 3, 1), d(2026, 11, 28), d(2025, 3, 1), d(2026, 11, 28), false,
                List.of(new DateWindow(d(2025, 3, 1), d(2025, 11, 30)),
                        new DateWindow(d(2026, 3, 9), d(2026, 11, 28)))));
    }

    @Test
    @DisplayName("LAST_YEARS=1 is the same window as the academic year")
    void lastYearsOne() {
        annual2026();

        ResolvedPeriod result = resolve(d(2026, 10, 10), LAST_YEARS, null, null, 1);

        assertThat(result.windows()).containsExactly(new DateWindow(d(2026, 3, 9), d(2026, 10, 10)));
        assertThat(result.years()).isEqualTo(1);
        assertThat(result.year()).isNull();
        assertThat(result.semester()).isNull();
    }

    @Test
    @DisplayName("LAST_YEARS=10 gives ten windows; 0, 11 and null answer an invalid selection")
    void lastYearsBounds() {
        annual2026();

        ResolvedPeriod ten = resolve(d(2026, 10, 10), LAST_YEARS, null, null, 10);
        assertThat(ten.windows()).hasSize(10);
        assertThat(ten.periodStart()).isEqualTo(d(2017, 3, 1));
        assertThatThrownBy(() -> resolve(d(2026, 10, 10), LAST_YEARS, null, null, 0))
                .isInstanceOf(InvalidSelectionException.class);
        assertThatThrownBy(() -> resolve(d(2026, 10, 10), LAST_YEARS, null, null, 11))
                .isInstanceOf(InvalidSelectionException.class);
        assertThatThrownBy(() -> resolve(d(2026, 10, 10), LAST_YEARS, null, null, null))
                .isInstanceOf(InvalidSelectionException.class);
    }

    @Test
    @DisplayName("parameters that do not belong to the type are ignored")
    void irrelevantParametersAreIgnored() {
        annual2026();

        assertThat(resolve(d(2026, 5, 15), TERM, null, null, 5)).isEqualTo(expected(TERM, 2026, 1,
                d(2026, 3, 9), d(2026, 7, 5), d(2026, 5, 15), true));
        assertThat(resolve(d(2026, 5, 15), ACADEMIC_YEAR, null, 2, 5)).isEqualTo(expected(ACADEMIC_YEAR, 2026, 0,
                d(2026, 3, 9), d(2026, 11, 28), d(2026, 5, 15), true));
        assertThat(resolve(d(2026, 10, 10), LAST_YEARS, 2020, 1, 1).windows())
                .containsExactly(new DateWindow(d(2026, 3, 9), d(2026, 10, 10)));
    }
}
