package ar.edu.utn.frc.siga.settings.dto.response;

import ar.edu.utn.frc.siga.settings.model.RiskLevel;
import ar.edu.utn.frc.siga.settings.model.SettingType;

public record SettingResponseDto(
        String key,
        SettingType type,
        String value,
        RiskLevel riskLevel,
        String min,
        String max,
        String warning) {
}
