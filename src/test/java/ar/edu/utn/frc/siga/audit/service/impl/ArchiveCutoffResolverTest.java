package ar.edu.utn.frc.siga.audit.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.audit.dto.response.AuditArchiveOutcome;
import ar.edu.utn.frc.siga.audit.service.impl.ArchiveCutoffResolver.ArchiveCutoff;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ArchiveCutoffResolver")
class ArchiveCutoffResolverTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);
    private static final LocalDate START_2024 = LocalDate.of(2024, 3, 4);
    private static final LocalDate START_2025 = LocalDate.of(2025, 3, 3);
    private static final LocalDate START_2026 = LocalDate.of(2026, 3, 2);

    private static AcademicPeriodResponseDto period(Integer year, int semester, LocalDate start) {
        return new AcademicPeriodResponseDto(year, semester, start, start == null ? null : start.plusMonths(5));
    }

    private static Optional<ArchiveCutoff> cutoffOf(List<AcademicPeriodResponseDto> periods, LocalDate today) {
        return Optional.ofNullable(ArchiveCutoffResolver.resolve(periods, today).cutoff());
    }

    private static AuditArchiveOutcome failureOf(List<AcademicPeriodResponseDto> periods, LocalDate today) {
        return ArchiveCutoffResolver.resolve(periods, today).failure();
    }

    private static List<AcademicPeriodResponseDto> threeCycles() {
        return new ArrayList<>(List.of(period(2024, 0, START_2024), period(2025, 0, START_2025),
                period(2026, 0, START_2026)));
    }

    @Test
    @DisplayName("con el ciclo 2026 en curso conserva desde 2025 y corta en su inicio")
    void currentCycleKeepsThePreviousOne() {
        Optional<ArchiveCutoff> cutoff = cutoffOf(threeCycles(), TODAY);

        assertThat(cutoff).contains(new ArchiveCutoff(2025, START_2025));
    }

    @Test
    @DisplayName("el instante de corte es la medianoche del inicio del ciclo conservado")
    void instantIsStartOfDay() {
        ArchiveCutoff cutoff = cutoffOf(threeCycles(), TODAY).orElseThrow();

        assertThat(cutoff.instant()).isEqualTo(LocalDateTime.of(2025, 3, 3, 0, 0));
    }

    @Test
    @DisplayName("antes del inicio del año sigue en curso el ciclo anterior: conserva desde 2024")
    void beforeCycleStartThePreviousYearIsStillCurrent() {
        Optional<ArchiveCutoff> cutoff = cutoffOf(threeCycles(), LocalDate.of(2026, 2, 15));

        assertThat(cutoff).contains(new ArchiveCutoff(2024, START_2024));
    }

    @Test
    @DisplayName("el día de inicio del ciclo ya está en curso; con el corte a exactamente 12 meses sí archiva")
    void onTheStartDayTheNewCycleIsCurrent() {
        Optional<ArchiveCutoff> cutoff = cutoffOf(threeCycles(), START_2026.plusDays(1));

        assertThat(cutoff).contains(new ArchiveCutoff(2025, START_2025));
    }

    @Test
    @DisplayName("un día antes del límite de 12 meses (corte 03/03/2025, hoy 02/03/2026) devuelve INVALID_PERIODS")
    void cutoffYoungerThanTwelveMonthsIsInvalid() {
        assertThat(failureOf(threeCycles(), START_2026)).isEqualTo(AuditArchiveOutcome.INVALID_PERIODS);
        assertThat(cutoffOf(threeCycles(), START_2026)).isEmpty();
    }

    @Test
    @DisplayName("el día anterior al inicio del ciclo todavía conserva desde 2024")
    void theDayBeforeTheStartTheOldCycleIsCurrent() {
        Optional<ArchiveCutoff> cutoff = cutoffOf(threeCycles(), START_2026.minusDays(1));

        assertThat(cutoff).contains(new ArchiveCutoff(2024, START_2024));
    }

    @Test
    @DisplayName("sin períodos del año actual no hay corte")
    void missingCurrentYearHasNoCutoff() {
        List<AcademicPeriodResponseDto> periods = List.of(period(2024, 0, START_2024), period(2025, 0, START_2025));

        assertThat(failureOf(periods, TODAY)).isEqualTo(AuditArchiveOutcome.NO_PERIODS);
    }

    @Test
    @DisplayName("con el año actual pero sin el ciclo previo no hay corte")
    void missingPreviousCycleHasNoCutoff() {
        List<AcademicPeriodResponseDto> periods = List.of(period(2024, 0, START_2024), period(2026, 0, START_2026));

        assertThat(failureOf(periods, TODAY)).isEqualTo(AuditArchiveOutcome.NO_PERIODS);
    }

    @Test
    @DisplayName("antes del inicio del año, sin el ciclo de dos años atrás no hay corte")
    void missingCycleBeforeThePreviousOneHasNoCutoffInFebruary() {
        List<AcademicPeriodResponseDto> periods = List.of(period(2025, 0, START_2025), period(2026, 0, START_2026));

        assertThat(failureOf(periods, LocalDate.of(2026, 2, 15))).isEqualTo(AuditArchiveOutcome.NO_PERIODS);
    }

    @Test
    @DisplayName("sin ningún período no hay corte")
    void noPeriodsHasNoCutoff() {
        assertThat(failureOf(List.of(), TODAY)).isEqualTo(AuditArchiveOutcome.NO_PERIODS);
    }

    @Test
    @DisplayName("el inicio del ciclo es la menor startDate del año aunque el anual venga después en la lista")
    void cycleStartIsTheEarliestStartDateOfTheYear() {
        List<AcademicPeriodResponseDto> periods = threeCycles();
        periods.removeIf(p -> p.year() == 2025);
        periods.add(period(2025, 1, LocalDate.of(2025, 3, 10)));
        periods.add(period(2025, 0, START_2025));
        periods.add(period(2025, 2, LocalDate.of(2025, 8, 4)));

        assertThat(cutoffOf(periods, TODAY)).contains(new ArchiveCutoff(2025, START_2025));
    }

    @Test
    @DisplayName("un período con startDate nulo se ignora y no pisa al que sí tiene fecha")
    void nullStartDateIsIgnored() {
        List<AcademicPeriodResponseDto> periods = threeCycles();
        periods.add(period(2025, 1, null));

        assertThat(cutoffOf(periods, TODAY)).contains(new ArchiveCutoff(2025, START_2025));
    }

    @Test
    @DisplayName("un año con todas las startDate nulas cuenta como faltante")
    void yearWithOnlyNullStartDatesIsMissing() {
        List<AcademicPeriodResponseDto> periods = threeCycles();
        periods.removeIf(p -> p.year() == 2025);
        periods.add(period(2025, 0, null));
        periods.add(period(2025, 1, null));

        assertThat(failureOf(periods, TODAY)).isEqualTo(AuditArchiveOutcome.NO_PERIODS);
    }

    @Test
    @DisplayName("un período con año nulo se ignora")
    void nullYearIsIgnored() {
        List<AcademicPeriodResponseDto> periods = threeCycles();
        periods.add(period(null, 0, LocalDate.of(2020, 1, 1)));

        assertThat(cutoffOf(periods, TODAY)).contains(new ArchiveCutoff(2025, START_2025));
    }

    @Test
    @DisplayName("si el inicio del ciclo previo es igual al del ciclo en curso devuelve INVALID_PERIODS")
    void previousCycleStartEqualToCurrentIsInvalid() {
        List<AcademicPeriodResponseDto> periods = List.of(period(2025, 0, START_2026), period(2026, 0, START_2026));

        assertThat(failureOf(periods, TODAY)).isEqualTo(AuditArchiveOutcome.INVALID_PERIODS);
    }

    @Test
    @DisplayName("si el inicio del ciclo previo es posterior al del ciclo en curso devuelve INVALID_PERIODS")
    void previousCycleStartAfterCurrentIsInvalid() {
        List<AcademicPeriodResponseDto> periods = List.of(period(2025, 0, START_2026.plusDays(30)),
                period(2026, 0, START_2026));

        assertThat(failureOf(periods, TODAY)).isEqualTo(AuditArchiveOutcome.INVALID_PERIODS);
    }

    @Test
    @DisplayName("un período del año en curso cargado en febrero adelanta el ciclo y el corte queda a menos de 12 meses: INVALID_PERIODS")
    void periodLoadedInFebruaryMakesTheCutoffTooRecent() {
        List<AcademicPeriodResponseDto> periods = threeCycles();
        periods.add(period(2026, 1, LocalDate.of(2026, 2, 10)));

        assertThat(failureOf(periods, LocalDate.of(2026, 2, 15))).isEqualTo(AuditArchiveOutcome.INVALID_PERIODS);
    }

    @Test
    @DisplayName("antes del inicio del año, sin períodos del ciclo en curso (el del año anterior) devuelve NO_PERIODS")
    void missingCurrentCycleOfThePreviousYearHasNoPeriods() {
        List<AcademicPeriodResponseDto> periods = List.of(period(2024, 0, START_2024), period(2026, 0, START_2026));

        assertThat(failureOf(periods, LocalDate.of(2026, 2, 15))).isEqualTo(AuditArchiveOutcome.NO_PERIODS);
    }

    @Test
    @DisplayName("una resolución exitosa no trae failure")
    void successHasNoFailure() {
        assertThat(failureOf(threeCycles(), TODAY)).isNull();
    }
}
