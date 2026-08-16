package ar.edu.utn.frc.siga.events.config;

import ar.edu.utn.frc.siga.settings.api.SettingsReader;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

@Component
@RequiredArgsConstructor
class EventScheduleSettingsProvider implements EventScheduleSettings {

    private final SettingsReader settingsReader;

    @Override
    public LocalTime getStart() {
        return settingsReader.getTime(SettingKey.EVENTS_HOURS_START);
    }

    @Override
    public LocalTime getEnd() {
        return settingsReader.getTime(SettingKey.EVENTS_HOURS_END);
    }
}
