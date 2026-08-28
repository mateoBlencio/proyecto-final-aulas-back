package ar.edu.utn.frc.siga.space.config;

import ar.edu.utn.frc.siga.settings.api.SettingsReader;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SpaceSettingsProvider implements SpaceSettings {

    private final SettingsReader settingsReader;

    @Override
    public boolean isFilterInactiveBuildings() {
        return settingsReader.getBoolean(SettingKey.SPACE_FILTER_INACTIVE_BUILDINGS);
    }
}
