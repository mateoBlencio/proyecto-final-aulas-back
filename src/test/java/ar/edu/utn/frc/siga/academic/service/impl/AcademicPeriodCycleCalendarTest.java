package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.audit.AcademicCycleCalendar.PeriodStart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AcademicPeriodCycleCalendar")
class AcademicPeriodCycleCalendarTest {

    @Mock
    private AcademicPeriodService academicPeriodService;

    @InjectMocks
    private AcademicPeriodCycleCalendar calendar;

    @Test
    @DisplayName("devuelve año y fecha de inicio de cada período activo")
    void mapsYearAndStartDate() {
        when(academicPeriodService.findActive()).thenReturn(List.of(
                new AcademicPeriodResponseDto(2025, 0, LocalDate.of(2025, 3, 3), LocalDate.of(2025, 12, 1)),
                new AcademicPeriodResponseDto(2026, 0, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 12, 1))));

        assertThat(calendar.activePeriodStarts()).containsExactly(
                new PeriodStart(2025, LocalDate.of(2025, 3, 3)),
                new PeriodStart(2026, LocalDate.of(2026, 3, 2)));
    }
}
