package ar.edu.utn.frc.siga.dashboard.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.HolidayResponseDto;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.academic.service.HolidayService;
import ar.edu.utn.frc.siga.dashboard.model.Availability;
import ar.edu.utn.frc.siga.dashboard.model.ExcludedDay;
import ar.edu.utn.frc.siga.dashboard.model.ExcludedDayReason;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Which days of a range count for the dashboard: Monday to Saturday, no holiday, no academic recess. */
@Component
@RequiredArgsConstructor
public class AvailabilityCalendar {

    private static final String RECESS_DESCRIPTION = "Receso académico";

    private final HolidayService holidayService;
    private final AcademicPeriodService academicPeriodService;

    public Availability resolve(LocalDate from, LocalDate to) {
        Map<LocalDate, String> holidays = new HashMap<>();
        for (HolidayResponseDto holiday : holidayService.findBetween(from, to)) {
            holidays.put(holiday.date(), holiday.description());
        }
        List<AcademicPeriodResponseDto> recesses = academicPeriodService.findRecessesBetween(from, to);

        Set<LocalDate> available = new HashSet<>();
        List<ExcludedDay> excluded = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            if (day.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            if (holidays.containsKey(day)) {
                excluded.add(new ExcludedDay(day, ExcludedDayReason.HOLIDAY, holidays.get(day)));
            } else if (isInRecess(day, recesses)) {
                excluded.add(new ExcludedDay(day, ExcludedDayReason.RECESS, RECESS_DESCRIPTION));
            } else {
                available.add(day);
            }
        }
        return new Availability(available, excluded);
    }

    private static boolean isInRecess(LocalDate day, List<AcademicPeriodResponseDto> recesses) {
        return recesses.stream().anyMatch(period -> !day.isBefore(period.recessStart()) && !day.isAfter(period.recessEnd()));
    }
}
