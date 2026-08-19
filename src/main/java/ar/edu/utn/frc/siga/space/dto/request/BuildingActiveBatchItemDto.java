package ar.edu.utn.frc.siga.space.dto.request;

import jakarta.validation.constraints.NotNull;

public record BuildingActiveBatchItemDto(
    @NotNull Integer id,
    @NotNull Boolean active
) {
}
