package ar.edu.utn.frc.siga.sysacad.internal.config;

import ar.edu.utn.frc.siga.settings.api.SettingsReader;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SysacadRuntimeSettings {

    private final SettingsReader settings;

    public SysacadWriteMode writeMode() {
        return SysacadWriteMode.valueOf(settings.getString(SettingKey.SYSACAD_WRITE_MODE));
    }

    public CommissionMessageMode commissionMessageMode() {
        return CommissionMessageMode.valueOf(settings.getString(SettingKey.SYSACAD_AUTOGESTION_MODE));
    }

    public Duration jobDelay() {
        return Duration.ofMinutes(settings.getLong(SettingKey.SYSACAD_WRITE_JOB_DELAY_MINUTES));
    }

    public Duration viewReadInterval() {
        return Duration.ofMinutes(settings.getLong(SettingKey.SYSACAD_WRITE_VIEW_READ_INTERVAL_MINUTES));
    }
}
