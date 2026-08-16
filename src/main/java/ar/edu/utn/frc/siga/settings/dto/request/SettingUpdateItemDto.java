package ar.edu.utn.frc.siga.settings.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SettingUpdateItemDto(
        @NotBlank(message = "La clave es obligatoria") String key,
        @NotNull(message = "El valor es obligatorio") String value) {
}
