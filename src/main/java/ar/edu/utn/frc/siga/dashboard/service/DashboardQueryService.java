package ar.edu.utn.frc.siga.dashboard.service;

import ar.edu.utn.frc.siga.dashboard.dto.response.AllocationTrendResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.DashboardBadgesResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.DashboardPeriodResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.DashboardReportResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.DashboardWeekResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.RoomRequestStatsResponseDto;
import ar.edu.utn.frc.siga.dashboard.model.DashboardPeriodType;
import java.time.LocalDate;
import java.time.YearMonth;

public interface DashboardQueryService {

    /** Campus and per-building view of the week containing {@code weekOf} (default: the current week). */
    DashboardWeekResponseDto week(LocalDate weekOf);

    /** Light counters for the menu: requests waiting for the user, expiring requests, classes without room, conflicts. */
    DashboardBadgesResponseDto badges();

    /** Resolved, pending and expired room request items of the last {@code years} years, by building. */
    RoomRequestStatsResponseDto roomRequests(int years);

    /** Monthly series from the closed-month snapshots plus the current month computed live (partial). */
    AllocationTrendResponseDto allocationTrend();

    /** Report of a closed month read from its snapshot. 404 when the month has none. */
    DashboardReportResponseDto report(YearMonth month);

    /** Term, academic year or current month, from its start up to today. */
    DashboardPeriodResponseDto period(DashboardPeriodType type);
}
