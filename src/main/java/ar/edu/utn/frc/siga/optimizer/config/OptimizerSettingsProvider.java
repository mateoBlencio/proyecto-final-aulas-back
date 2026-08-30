package ar.edu.utn.frc.siga.optimizer.config;

import ar.edu.utn.frc.siga.settings.api.SettingsReader;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OptimizerSettingsProvider implements OptimizerSettings {

    private final SettingsReader settingsReader;

    @Override
    public long getUnimprovedSecondsLimit() {
        return settingsReader.getLong(SettingKey.OPTIMIZER_UNIMPROVED_SECONDS_LIMIT);
    }

    @Override
    public long getSolverSecondsSpentLimit() {
        return settingsReader.getLong(SettingKey.OPTIMIZER_SOLVER_SECONDS_SPENT_LIMIT);
    }

    @Override
    public int getWeight(SettingKey key) {
        return settingsReader.getInt(key);
    }
}
