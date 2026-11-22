package ar.edu.utn.frc.siga.dashboard.dto.response;

import java.util.List;

public record RoomRequestStatsResponseDto(boolean restrictedScope, List<RoomRequestYearResponseDto> years) {
}
