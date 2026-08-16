package ar.edu.utn.frc.siga.settings.mapper;

import ar.edu.utn.frc.siga.settings.config.SettingsCatalogProperties;
import ar.edu.utn.frc.siga.settings.dto.response.SettingResponseDto;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SettingResponseMapper {

    private final SettingsCatalogProperties catalog;

    public SettingResponseDto toDto(SettingKey key, String value) {
        return new SettingResponseDto(
                key.getKey(), key.getType(), value, key.getRisk(),
                catalog.min(key), catalog.max(key), key.getWarning());
    }
}
