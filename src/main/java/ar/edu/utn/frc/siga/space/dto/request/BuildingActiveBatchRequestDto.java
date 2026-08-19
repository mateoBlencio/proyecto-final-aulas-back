package ar.edu.utn.frc.siga.space.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BuildingActiveBatchRequestDto(
    @NotEmpty(message = "Debe incluir al menos un edificio") @Valid List<BuildingActiveBatchItemDto> buildings
) {
}
