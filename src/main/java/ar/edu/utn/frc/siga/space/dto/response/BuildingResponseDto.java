package ar.edu.utn.frc.siga.space.dto.response;

import org.springframework.modulith.NamedInterface;

/**
 * Representación de un edificio para consumo por API/clientes.
 */
@NamedInterface("api")
public record BuildingResponseDto(
        Integer id,
        String name,
        Integer floorCount,
        Boolean active
) {
}
