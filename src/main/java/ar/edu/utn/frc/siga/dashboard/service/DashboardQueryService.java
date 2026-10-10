package ar.edu.utn.frc.siga.dashboard.service;

import ar.edu.utn.frc.siga.dashboard.dto.response.DashboardBadgesResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.DashboardWeekResponseDto;
import ar.edu.utn.frc.siga.dashboard.dto.response.RoomRequestStatsResponseDto;
import java.time.LocalDate;

public interface DashboardQueryService {

    /** Campus and per-building view of the week containing {@code weekOf} (default: the current week). */
    DashboardWeekResponseDto week(LocalDate weekOf);

    /** Light counters for the menu: requests waiting for the user, expiring requests, classes without room, conflicts. */
    DashboardBadgesResponseDto badges();

    /** Resolved, pending and expired room request items of the last {@code years} years, by building. */
    RoomRequestStatsResponseDto roomRequests(int years);
}
