package ar.edu.utn.frc.siga.auth.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateUserEnabledRequestDto(
        @NotNull Boolean enabled) {
}
