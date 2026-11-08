package ar.edu.utn.frc.siga.settings.dto.response;

import ar.edu.utn.frc.siga.settings.model.RiskLevel;
import ar.edu.utn.frc.siga.settings.model.SettingType;

import java.util.List;

public record SettingResponseDto(
        String key,
        SettingType type,
        String value,
        String defaultValue,
        RiskLevel riskLevel,
        String min,
        String max,
        List<String> options,
        String warning) {
}
