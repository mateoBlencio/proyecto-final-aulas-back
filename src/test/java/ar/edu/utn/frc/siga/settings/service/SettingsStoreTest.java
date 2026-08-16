package ar.edu.utn.frc.siga.settings.service;

import ar.edu.utn.frc.siga.settings.api.SettingChangedEvent;
import ar.edu.utn.frc.siga.settings.model.Setting;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import ar.edu.utn.frc.siga.settings.repository.SettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettingsStore")
class SettingsStoreTest {

    @Mock
    private SettingRepository repository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SettingsStore store;

    @Test
    @DisplayName("getRaw cae al default del enum cuando la clave no está cacheada")
    void getRawFallsBackToDefault() {
        assertThat(store.getRaw(SettingKey.OPTIMIZER_WEIGHT_OVERCROWDING)).isEqualTo("100000");
    }

    @Test
    @DisplayName("Los getters tipados parsean el default del enum")
    void typedGettersParseDefaults() {
        assertThat(store.getInt(SettingKey.OPTIMIZER_WEIGHT_OVERCROWDING)).isEqualTo(100000);
        assertThat(store.getLong(SettingKey.OPTIMIZER_UNIMPROVED_SECONDS_LIMIT)).isEqualTo(10L);
        assertThat(store.getBoolean(SettingKey.OPTIMIZER_CONSTRAINT_MINIMIZE_OVERCROWDING_ENABLED)).isTrue();
        assertThat(store.getTime(SettingKey.EVENTS_HOURS_START)).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    @DisplayName("write persiste, publica el evento y (sin tx) actualiza el cache")
    void writePersistsPublishesAndUpdatesCache() {
        SettingKey key = SettingKey.OPTIMIZER_WEIGHT_OVERCROWDING;
        when(repository.findById(key.getKey())).thenReturn(Optional.empty());

        store.write(key, "42");

        verify(repository).save(any(Setting.class));
        verify(eventPublisher).publishEvent(new SettingChangedEvent(key));
        assertThat(store.getRaw(key)).isEqualTo("42");
        assertThat(store.getInt(key)).isEqualTo(42);
    }

    @Test
    @DisplayName("write reutiliza la fila existente y sólo cambia el valor")
    void writeReusesExistingRow() {
        SettingKey key = SettingKey.PREVIEW_TTL_MINUTES;
        Setting existing = new Setting(key.getKey(), "30");
        when(repository.findById(key.getKey())).thenReturn(Optional.of(existing));

        store.write(key, "60");

        assertThat(existing.getValue()).isEqualTo("60");
        verify(repository).save(existing);
        assertThat(store.getLong(key)).isEqualTo(60L);
    }
}
