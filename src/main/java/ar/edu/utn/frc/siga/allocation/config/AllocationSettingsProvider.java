package ar.edu.utn.frc.siga.allocation.config;

import ar.edu.utn.frc.siga.settings.api.SettingsReader;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AllocationSettingsProvider implements AllocationSettings {

    private final SettingsReader settingsReader;

    @Override
    public int getMaxOverlapMinutes() {
        return settingsReader.getInt(SettingKey.ALLOCATION_MAX_OVERLAP_MINUTES);
    }
}
