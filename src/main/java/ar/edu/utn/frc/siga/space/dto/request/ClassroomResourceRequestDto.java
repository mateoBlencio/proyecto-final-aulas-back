package ar.edu.utn.frc.siga.space.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ClassroomResourceRequestDto(
        @NotBlank String code,
        @NotNull @PositiveOrZero Integer quantity
) {
}
