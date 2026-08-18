package ar.edu.utn.frc.siga.settings.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateSettingRequestDto(
        @NotNull(message = "El valor es obligatorio") String value) {
}
