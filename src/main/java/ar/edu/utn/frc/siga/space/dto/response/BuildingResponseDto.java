package ar.edu.utn.frc.siga.space.dto.response;

/**
 * Representación de un edificio para consumo por API/clientes.
 */
public record BuildingResponseDto(
        Integer id,
        String name,
        Integer floorCount,
        Boolean active
) {
}
