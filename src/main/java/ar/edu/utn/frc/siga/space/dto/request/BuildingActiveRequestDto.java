package ar.edu.utn.frc.siga.space.dto.request;

import jakarta.validation.constraints.NotNull;

public record BuildingActiveRequestDto(
    @NotNull Boolean active
) {
}
