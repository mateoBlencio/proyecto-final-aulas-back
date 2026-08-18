package ar.edu.utn.frc.siga.settings.api;

import ar.edu.utn.frc.siga.settings.model.SettingKey;
import org.springframework.modulith.NamedInterface;

import java.time.LocalTime;

@NamedInterface("api")
public interface SettingsReader {

    int getInt(SettingKey key);

    long getLong(SettingKey key);

    boolean getBoolean(SettingKey key);

    LocalTime getTime(SettingKey key);
}
