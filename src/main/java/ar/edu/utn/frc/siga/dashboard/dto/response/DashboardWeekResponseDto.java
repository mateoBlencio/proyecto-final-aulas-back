package ar.edu.utn.frc.siga.dashboard.dto.response;

import java.time.LocalDate;
import java.util.List;

public record DashboardWeekResponseDto(
        LocalDate weekStart, LocalDate weekEnd, boolean currentWeek, boolean restrictedScope,
        DashboardParametersResponseDto parameters,
        List<ExcludedDayResponseDto> excludedDays,
        WeekCampusResponseDto campus,
        List<WeekBuildingResponseDto> buildings) {
}
