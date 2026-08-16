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
    public int getOvercrowdingWeight() {
        return settingsReader.getInt(SettingKey.OPTIMIZER_WEIGHT_OVERCROWDING);
    }

    @Override
    public int getSameCommissionDiffRoomWeight() {
        return settingsReader.getInt(SettingKey.OPTIMIZER_WEIGHT_SAME_COMMISSION_DIFF_ROOM);
    }

    @Override
    public int getSameCommissionDiffBuildingWeight() {
        return settingsReader.getInt(SettingKey.OPTIMIZER_WEIGHT_SAME_COMMISSION_DIFF_BUILDING);
    }

    @Override
    public int getUnusedCapacityWeight() {
        return settingsReader.getInt(SettingKey.OPTIMIZER_WEIGHT_UNUSED_CAPACITY);
    }

    @Override
    public boolean isMinimizeOvercrowdingEnabled() {
        return settingsReader.getBoolean(SettingKey.OPTIMIZER_CONSTRAINT_MINIMIZE_OVERCROWDING_ENABLED);
    }

    @Override
    public boolean isMinimizeUnusedCapacityEnabled() {
        return settingsReader.getBoolean(SettingKey.OPTIMIZER_CONSTRAINT_MINIMIZE_UNUSED_CAPACITY_ENABLED);
    }

    @Override
    public boolean isPreferSameRoomSameCommissionEnabled() {
        return settingsReader.getBoolean(SettingKey.OPTIMIZER_CONSTRAINT_PREFER_SAME_ROOM_SAME_COMMISSION_ENABLED);
    }

    @Override
    public boolean isPreferSameBuildingSameCommissionEnabled() {
        return settingsReader.getBoolean(SettingKey.OPTIMIZER_CONSTRAINT_PREFER_SAME_BUILDING_SAME_COMMISSION_ENABLED);
    }
}
