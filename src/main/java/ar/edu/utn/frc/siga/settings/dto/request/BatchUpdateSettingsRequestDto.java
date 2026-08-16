package ar.edu.utn.frc.siga.settings.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchUpdateSettingsRequestDto(
        @NotEmpty(message = "Debe incluir al menos un setting") @Valid List<SettingUpdateItemDto> settings) {
}
