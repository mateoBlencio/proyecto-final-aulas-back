package ar.edu.utn.frc.siga.dashboard.dto.response;

import java.time.Instant;

public record OpenRoomRequestsResponseDto(long count, Instant oldestSince) {
}
