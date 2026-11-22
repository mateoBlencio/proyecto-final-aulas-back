package ar.edu.utn.frc.siga.dashboard.dto.response;

/** Pending is null except for the current year. */
public record RoomRequestCountsResponseDto(long resolved, Long pending, long expired) {
}
