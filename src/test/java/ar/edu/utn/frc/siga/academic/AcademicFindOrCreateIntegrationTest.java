package ar.edu.utn.frc.siga.academic;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AcademicPeriod es dato propio de cada import/dictado (find-or-create), a diferencia de
 * Specialty/StudyPlan/Subject que ya son catálogo (ver {@link AcademicFindByCodeIntegrationTest}).
 * Sin endpoint HTTP propio: se inyecta el service directamente.
 */
@DisplayName("AcademicPeriod findOrCreate (integración)")
class AcademicFindOrCreateIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger((int) (System.nanoTime() % 1_000_000));

    @Autowired
    private AcademicPeriodService academicPeriodService;
    @Autowired
    private AcademicPeriodRepository academicPeriodRepository;

    private static int nextCode() {
        return SEQ.incrementAndGet();
    }

    @Test
    @DisplayName("periodo academico unico por (year, term): segunda llamada reusa sin duplicar")
    void findOrCreate_academicPeriod_reusesByYearAndTerm() {
        int year = 2100 + (nextCode() % 500);
        long before = academicPeriodRepository.count();

        FindOrCreateResult<AcademicPeriodResponseDto> first =
                academicPeriodService.findOrCreate(year, TermType.PRIMER_CUATRIMESTRE);
        FindOrCreateResult<AcademicPeriodResponseDto> second =
                academicPeriodService.findOrCreate(year, TermType.PRIMER_CUATRIMESTRE);

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(academicPeriodRepository.count()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("fechas de TermType persistidas: ANUAL 1-mar a 30-nov, 1er cuat. 1-mar a 31-jul, 2do cuat. 1-ago a 30-nov")
    void findOrCreate_academicPeriod_persistsTermTypeDates() {
        int yearAnual = 2100 + (nextCode() % 500);
        int yearPrimero = 2100 + (nextCode() % 500);
        int yearSegundo = 2100 + (nextCode() % 500);

        AcademicPeriodResponseDto anual = academicPeriodService.findOrCreate(yearAnual, TermType.ANUAL).value();
        AcademicPeriodResponseDto primero =
                academicPeriodService.findOrCreate(yearPrimero, TermType.PRIMER_CUATRIMESTRE).value();
        AcademicPeriodResponseDto segundo =
                academicPeriodService.findOrCreate(yearSegundo, TermType.SEGUNDO_CUATRIMESTRE).value();

        assertThat(anual.startDate()).isEqualTo(LocalDate.of(yearAnual, 3, 1));
        assertThat(anual.endDate()).isEqualTo(LocalDate.of(yearAnual, 11, 30));

        assertThat(primero.startDate()).isEqualTo(LocalDate.of(yearPrimero, 3, 1));
        assertThat(primero.endDate()).isEqualTo(LocalDate.of(yearPrimero, 7, 31));

        assertThat(segundo.startDate()).isEqualTo(LocalDate.of(yearSegundo, 8, 1));
        assertThat(segundo.endDate()).isEqualTo(LocalDate.of(yearSegundo, 11, 30));
    }
}
