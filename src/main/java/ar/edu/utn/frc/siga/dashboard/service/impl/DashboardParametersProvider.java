package ar.edu.utn.frc.siga.dashboard.service.impl;

import ar.edu.utn.frc.siga.dashboard.config.DashboardProperties;
import ar.edu.utn.frc.siga.dashboard.model.DashboardParameters;
import ar.edu.utn.frc.siga.dashboard.model.DashboardRules;
import ar.edu.utn.frc.siga.settings.api.SettingsReader;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DashboardParametersProvider {

    private final DashboardProperties properties;
    private final SettingsReader settingsReader;

    public DashboardParameters current() {
        return new DashboardParameters(
                properties.underusedThresholdPercent(),
                settingsReader.getInt(SettingKey.ALLOCATION_MAX_OVERLAP_MINUTES),
                settingsReader.getTime(SettingKey.EVENTS_HOURS_START),
                settingsReader.getTime(SettingKey.EVENTS_HOURS_END),
                DashboardRules.RULES_VERSION);
    }
}
