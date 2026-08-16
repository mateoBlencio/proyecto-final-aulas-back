package ar.edu.utn.frc.siga.settings.api;

import ar.edu.utn.frc.siga.settings.model.SettingKey;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record SettingChangedEvent(SettingKey key) {
}
