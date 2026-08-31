package ar.edu.utn.frc.siga.space.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ClassroomResourceRequestDto(
        @NotNull Long resourceTypeId,
        @NotNull @PositiveOrZero Integer quantity
) {
}
