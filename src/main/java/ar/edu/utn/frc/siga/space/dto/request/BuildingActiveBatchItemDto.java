package ar.edu.utn.frc.siga.space.dto.request;

import jakarta.validation.constraints.NotNull;

public record BuildingActiveBatchItemDto(
    @NotNull Long id,
    @NotNull Boolean active
) {
}
