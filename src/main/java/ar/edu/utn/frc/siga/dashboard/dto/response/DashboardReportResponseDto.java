package ar.edu.utn.frc.siga.dashboard.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record DashboardReportResponseDto(
        YearMonth month, LocalDate from, LocalDate to,
        Instant generatedAt, String generatedBy, String regenerationReason, boolean regenerated,
        int schemaVersion, boolean restrictedScope,
        DashboardParametersResponseDto parameters,
        ReportCampusResponseDto campus,
        List<ReportBuildingResponseDto> buildings) {
}
