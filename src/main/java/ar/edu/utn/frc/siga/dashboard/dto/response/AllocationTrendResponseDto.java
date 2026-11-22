package ar.edu.utn.frc.siga.dashboard.dto.response;

import java.time.YearMonth;
import java.util.List;

public record AllocationTrendResponseDto(
        boolean restrictedScope,
        List<YearMonth> closedMonths,
        YearMonth baselineMonth,
        YearMonth latestClosedMonth,
        List<MonthlyAllocationResponseDto> series,
        MonthlyAllocationResponseDto currentMonth) {
}
