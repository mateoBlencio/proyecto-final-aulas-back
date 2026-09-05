package ar.edu.utn.frc.siga.space.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClassroomTypeRequestDto(
        @NotBlank @Size(max = 50) String description
) {
}
