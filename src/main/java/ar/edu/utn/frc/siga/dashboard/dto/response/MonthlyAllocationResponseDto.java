package ar.edu.utn.frc.siga.dashboard.dto.response;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record MonthlyAllocationResponseDto(
        YearMonth month, boolean partial, LocalDate from, LocalDate to,
        AllocationStatusResponseDto allocationStatus,
        Long unallocatedClasses,
        OccupancyResponseDto occupancy,
        List<SourceBreakdownResponseDto> bySource) {
}
