package ar.edu.utn.frc.siga.dashboard.dto.response;

import ar.edu.utn.frc.siga.dashboard.model.DashboardPeriodType;
import java.time.LocalDate;
import java.util.List;

public record DashboardPeriodResponseDto(
        DashboardPeriodType type, Integer year, Integer semester, Integer years,
        LocalDate periodStart, LocalDate periodEnd, LocalDate from, LocalDate to,
        boolean partial, boolean restrictedScope,
        DashboardParametersResponseDto parameters,
        List<ExcludedDayResponseDto> excludedDays,
        ReportCampusResponseDto campus,
        List<ReportBuildingResponseDto> buildings) {
}
