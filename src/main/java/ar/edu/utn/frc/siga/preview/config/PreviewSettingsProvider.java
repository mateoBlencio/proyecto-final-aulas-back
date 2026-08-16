package ar.edu.utn.frc.siga.preview.config;

import ar.edu.utn.frc.siga.settings.api.SettingsReader;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PreviewSettingsProvider implements PreviewSettings {

    private final SettingsReader settingsReader;

    @Override
    public int getDefaultTimeLimitSeconds() {
        return settingsReader.getInt(SettingKey.PREVIEW_DEFAULT_TIME_LIMIT_SECONDS);
    }

    @Override
    public long getTtlMinutes() {
        return settingsReader.getLong(SettingKey.PREVIEW_TTL_MINUTES);
    }
}
