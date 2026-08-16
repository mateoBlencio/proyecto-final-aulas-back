package ar.edu.utn.frc.siga.settings.config;

import ar.edu.utn.frc.siga.settings.model.Setting;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import ar.edu.utn.frc.siga.settings.repository.SettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettingsSeeder")
class SettingsSeederTest {

    @Mock
    private SettingRepository repository;

    @InjectMocks
    private SettingsSeeder seeder;

    @Test
    @DisplayName("Con la base vacía siembra una fila por cada SettingKey con su default")
    void seedsAllKeysWhenDatabaseEmpty() {
        when(repository.existsById(any())).thenReturn(false);

        seeder.run(null);

        ArgumentCaptor<Setting> captor = ArgumentCaptor.forClass(Setting.class);
        verify(repository, times(SettingKey.values().length)).save(captor.capture());

        List<Setting> saved = captor.getAllValues();
        assertThat(saved).hasSize(SettingKey.values().length);
        for (SettingKey key : SettingKey.values()) {
            assertThat(saved)
                    .anyMatch(s -> s.getKey().equals(key.getKey()) && s.getValue().equals(key.getDefaultValue()));
        }
    }

    @Test
    @DisplayName("Es idempotente: sólo siembra las claves que faltan")
    void seedsOnlyMissingKeys() {
        SettingKey existing = SettingKey.EVENTS_HOURS_START;
        when(repository.existsById(any())).thenReturn(false);
        when(repository.existsById(existing.getKey())).thenReturn(true);

        seeder.run(null);

        verify(repository, times(SettingKey.values().length - 1)).save(any(Setting.class));
        ArgumentCaptor<Setting> captor = ArgumentCaptor.forClass(Setting.class);
        verify(repository, times(SettingKey.values().length - 1)).save(captor.capture());
        assertThat(captor.getAllValues()).noneMatch(s -> s.getKey().equals(existing.getKey()));
    }

    @Test
    @DisplayName("Con la base ya sembrada no inserta nada")
    void seedsNothingWhenAllPresent() {
        when(repository.existsById(any())).thenReturn(true);

        seeder.run(null);

        verify(repository, never()).save(any(Setting.class));
    }
}
