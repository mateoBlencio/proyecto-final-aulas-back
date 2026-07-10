package ar.edu.utn.frc.siga.space.dto.response;

public record BuildingResponseDto(
        Integer id,
        String name,
        Integer floorCount,
        Boolean active
) {
}
