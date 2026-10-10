package ar.edu.utn.frc.siga.dashboard.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.HolidayResponseDto;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.academic.service.HolidayService;
import ar.edu.utn.frc.siga.dashboard.model.Availability;
import ar.edu.utn.frc.siga.dashboard.model.ExcludedDay;
import ar.edu.utn.frc.siga.dashboard.model.ExcludedDayReason;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AvailabilityCalendarTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 7, 6);

    private final HolidayService holidayService = mock(HolidayService.class);
    private final AcademicPeriodService academicPeriodService = mock(AcademicPeriodService.class);
    private final AvailabilityCalendar calendar = new AvailabilityCalendar(holidayService, academicPeriodService);

    private static AcademicPeriodResponseDto annualWithRecess(LocalDate start, LocalDate end) {
        return new AcademicPeriodResponseDto(1L, 2026, 0, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 30),
                start, end);
    }

    @Test
    void holidayAndRecessLeaveOnlyMondayTuesdayAndSaturday() {
        when(holidayService.findBetween(any(), any()))
                .thenReturn(List.of(new HolidayResponseDto(MONDAY.plusDays(2), "Independence Day")));
        when(academicPeriodService.findRecessesBetween(any(), any()))
                .thenReturn(List.of(annualWithRecess(MONDAY.plusDays(3), MONDAY.plusDays(4))));

        Availability availability = calendar.resolve(MONDAY, MONDAY.plusDays(6)); // Monday to Sunday

        assertThat(availability.availableDays())
                .containsExactlyInAnyOrder(MONDAY, MONDAY.plusDays(1), MONDAY.plusDays(5));
        assertThat(availability.excludedDays()).extracting(ExcludedDay::date, ExcludedDay::reason)
                .containsExactly(
                        tuple(MONDAY.plusDays(2), ExcludedDayReason.HOLIDAY),
                        tuple(MONDAY.plusDays(3), ExcludedDayReason.RECESS),
                        tuple(MONDAY.plusDays(4), ExcludedDayReason.RECESS));
    }

    @Test
    void dayThatIsHolidayAndRecessIsReportedAsHoliday() {
        LocalDate day = MONDAY.plusDays(1);
        when(holidayService.findBetween(any(), any())).thenReturn(List.of(new HolidayResponseDto(day, "Holiday")));
        when(academicPeriodService.findRecessesBetween(any(), any())).thenReturn(List.of(annualWithRecess(day, day)));

        Availability availability = calendar.resolve(day, day);

        assertThat(availability.availableDays()).isEmpty();
        assertThat(availability.excludedDays()).singleElement()
                .satisfies(excluded -> assertThat(excluded.reason()).isEqualTo(ExcludedDayReason.HOLIDAY));
    }

    @Test
    void sundayIsNeverListedAsExcludedEvenWhenItIsAHoliday() {
        LocalDate sunday = MONDAY.plusDays(6);
        when(holidayService.findBetween(any(), any())).thenReturn(List.of(new HolidayResponseDto(sunday, "Holiday")));
        when(academicPeriodService.findRecessesBetween(any(), any())).thenReturn(List.of());

        Availability availability = calendar.resolve(sunday, sunday);

        assertThat(availability.availableDays()).isEmpty();
        assertThat(availability.excludedDays()).isEmpty();
    }
}
