package ar.edu.utn.frc.siga.academic;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Narrow integration test of {@code findRecessesBetween} against the real query. Years 4000+ keep the fixtures
 * away from the periods other tests create.
 */
@Import(IntegrationTestData.class)
@DisplayName("AcademicPeriodService.findRecessesBetween (integración)")
class AcademicPeriodRecessQueryIntegrationTest extends AbstractIntegrationTest {

    private static final int ANNUAL_YEAR = 4000;
    private static final int FIRST_TERM_YEAR = 4001;
    private static final int DEACTIVATED_YEAR = 4002;
    private static final int NO_RECESS_YEAR = 4003;
    private static final LocalDate RECESS_START = LocalDate.of(ANNUAL_YEAR, 7, 6);
    private static final LocalDate RECESS_END = LocalDate.of(ANNUAL_YEAR, 7, 27);

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private AcademicPeriodRepository periodRepository;
    @Autowired
    private AcademicPeriodService periodService;

    @BeforeEach
    void createPeriods() {
        withRecess(ANNUAL_YEAR, TermType.ANUAL, false);
        withRecess(FIRST_TERM_YEAR, TermType.PRIMER_CUATRIMESTRE, false);
        withRecess(DEACTIVATED_YEAR, TermType.ANUAL, true);
        testData.periodoAcademico(NO_RECESS_YEAR, TermType.ANUAL);
    }

    private void withRecess(int year, TermType term, boolean deactivated) {
        AcademicPeriod period = testData.periodoAcademico(year, term);
        period.setRecessStart(LocalDate.of(year, 7, 6));
        period.setRecessEnd(LocalDate.of(year, 7, 27));
        if (deactivated && !period.isDeleted()) {
            period.deactivate();
        }
        periodRepository.save(period);
    }

    private List<Integer> yearsFound(LocalDate from, LocalDate to) {
        return periodService.findRecessesBetween(from, to).stream()
                .map(AcademicPeriodResponseDto::year)
                .filter(year -> year >= ANNUAL_YEAR)
                .toList();
    }

    @Test
    @DisplayName("a range ending on the recess start day includes the period, one ending the day before does not")
    void rangeEndingOnRecessStartBoundary() {
        assertThat(yearsFound(RECESS_START.minusDays(10), RECESS_START)).containsExactly(ANNUAL_YEAR);
        assertThat(yearsFound(RECESS_START.minusDays(10), RECESS_START.minusDays(1))).isEmpty();
    }

    @Test
    @DisplayName("a range starting on the recess end day includes the period, one starting the day after does not")
    void rangeStartingOnRecessEndBoundary() {
        assertThat(yearsFound(RECESS_END, RECESS_END.plusDays(10))).containsExactly(ANNUAL_YEAR);
        assertThat(yearsFound(RECESS_END.plusDays(1), RECESS_END.plusDays(10))).isEmpty();
    }

    @Test
    @DisplayName("a range inside the recess and a range wrapping it both include the period")
    void rangeInsideAndAroundTheRecess() {
        assertThat(yearsFound(RECESS_START.plusDays(3), RECESS_START.plusDays(5))).containsExactly(ANNUAL_YEAR);
        assertThat(yearsFound(RECESS_START.minusMonths(1), RECESS_END.plusMonths(1))).containsExactly(ANNUAL_YEAR);
    }

    @Test
    @DisplayName("a non-annual period with recess dates is not returned")
    void nonAnnualPeriodIsIgnored() {
        LocalDate day = LocalDate.of(FIRST_TERM_YEAR, 7, 10);

        assertThat(yearsFound(day, day)).isEmpty();
    }

    @Test
    @DisplayName("a deactivated annual period with recess dates is not returned")
    void deactivatedPeriodIsIgnored() {
        LocalDate day = LocalDate.of(DEACTIVATED_YEAR, 7, 10);

        assertThat(yearsFound(day, day)).isEmpty();
    }

    @Test
    @DisplayName("an annual period without recess dates is not returned")
    void periodWithoutRecessIsIgnored() {
        assertThat(yearsFound(LocalDate.of(NO_RECESS_YEAR, 1, 1), LocalDate.of(NO_RECESS_YEAR, 12, 31))).isEmpty();
    }
}
