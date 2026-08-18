package ar.edu.utn.frc.siga.space.dto.response;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record BuildingResponseDto(
        Integer id,
        String name,
        Integer floorCount,
        Boolean active
) {
}
