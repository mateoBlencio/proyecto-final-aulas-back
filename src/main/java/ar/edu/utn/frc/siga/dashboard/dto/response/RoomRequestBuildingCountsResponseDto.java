package ar.edu.utn.frc.siga.dashboard.dto.response;

public record RoomRequestBuildingCountsResponseDto(
        Long buildingId, Integer code, String name, long resolved, Long pending, long expired) {
}
