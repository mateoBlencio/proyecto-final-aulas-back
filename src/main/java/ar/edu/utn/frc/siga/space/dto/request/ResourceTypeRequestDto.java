package ar.edu.utn.frc.siga.space.dto.request;

import ar.edu.utn.frc.siga.space.model.ResourceValueKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResourceTypeRequestDto(
        @NotBlank @Size(max = 100) String name,
        @NotNull ResourceValueKind valueKind
) {
}
