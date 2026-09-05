package ar.edu.utn.frc.siga.optimizer.config;

import ar.edu.utn.frc.siga.settings.model.SettingKey;

public interface OptimizerSettings {

    long getUnimprovedSecondsLimit();

    long getSolverSecondsSpentLimit();

    int getWeight(SettingKey key);
}
