package ar.edu.utn.frc.siga.dashboard.dto.response;

import java.time.LocalTime;

public record DashboardParametersResponseDto(
        int underusedThresholdPercent, int maxOverlapMinutes,
        LocalTime hoursStart, LocalTime hoursEnd, int rulesVersion) {
}
