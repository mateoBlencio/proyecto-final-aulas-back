package ar.edu.utn.frc.siga.settings.service.impl;

import ar.edu.utn.frc.siga.settings.SettingsCatalogFixture;
import ar.edu.utn.frc.siga.settings.dto.request.SettingUpdateItemDto;
import ar.edu.utn.frc.siga.settings.dto.response.SettingResponseDto;
import ar.edu.utn.frc.siga.settings.exception.InvalidSettingValueException;
import ar.edu.utn.frc.siga.settings.mapper.SettingResponseMapper;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import ar.edu.utn.frc.siga.settings.service.SettingsStore;
import ar.edu.utn.frc.siga.settings.validator.SettingValueValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettingsServiceImpl: escritura y validación de la ventana horaria")
class SettingsServiceImplTest {

    @Mock
    private SettingsStore store;

    private SettingsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SettingsServiceImpl(
                store,
                new SettingValueValidator(SettingsCatalogFixture.catalog()),
                new SettingResponseMapper(SettingsCatalogFixture.catalog()));
    }

    private static SettingUpdateItemDto item(SettingKey key, String value) {
        return new SettingUpdateItemDto(key.getKey(), value);
    }

    @Test
    @DisplayName("update(inicio) posterior al fin vigente lanza y no escribe")
    void updateStartAfterCurrentEndThrowsAndDoesNotWrite() {
        when(store.getTime(SettingKey.EVENTS_HOURS_END)).thenReturn(LocalTime.of(21, 0));

        assertThatThrownBy(() -> service.update(SettingKey.EVENTS_HOURS_START.getKey(), "22:00"))
                .isInstanceOf(InvalidSettingValueException.class)
                .hasMessage("La hora de inicio de la ventana horaria (22:00) debe ser anterior a la hora de fin (21:00)");

        verify(store, never()).write(any(), any());
    }

    @Test
    @DisplayName("update(fin) anterior al inicio vigente lanza y no escribe")
    void updateEndBeforeCurrentStartThrowsAndDoesNotWrite() {
        when(store.getTime(SettingKey.EVENTS_HOURS_START)).thenReturn(LocalTime.of(8, 0));

        assertThatThrownBy(() -> service.update(SettingKey.EVENTS_HOURS_END.getKey(), "07:00"))
                .isInstanceOf(InvalidSettingValueException.class)
                .hasMessage("La hora de inicio de la ventana horaria (08:00) debe ser anterior a la hora de fin (07:00)");

        verify(store, never()).write(any(), any());
    }

    @Test
    @DisplayName("update(fin) posterior al inicio vigente escribe y devuelve el valor normalizado")
    void updateEndAfterCurrentStartWrites() {
        when(store.getTime(SettingKey.EVENTS_HOURS_START)).thenReturn(LocalTime.of(8, 0));

        SettingResponseDto result = service.update(SettingKey.EVENTS_HOURS_END.getKey(), "22:30");

        assertThat(result.key()).isEqualTo("events.hours.end");
        assertThat(result.value()).isEqualTo("22:30");
        verify(store).write(SettingKey.EVENTS_HOURS_END, "22:30");
    }

    @Test
    @DisplayName("lote con inicio 22:00 y fin 23:00 pasa aunque el fin vigente sea 21:00: usa el valor del lote")
    void batchUsesPendingValuesInsteadOfCurrentOnes() {
        List<SettingResponseDto> result = service.updateBatch(List.of(
                item(SettingKey.EVENTS_HOURS_START, "22:00"),
                item(SettingKey.EVENTS_HOURS_END, "23:00")));

        assertThat(result).extracting(SettingResponseDto::key)
                .containsExactly("events.hours.start", "events.hours.end");
        verify(store).write(SettingKey.EVENTS_HOURS_START, "22:00");
        verify(store).write(SettingKey.EVENTS_HOURS_END, "23:00");
        verify(store, never()).getTime(any());
    }

    @Test
    @DisplayName("lote con una clave válida y un inicio inválido no escribe ninguna clave")
    void batchWithInvalidWindowWritesNothing() {
        when(store.getTime(SettingKey.EVENTS_HOURS_END)).thenReturn(LocalTime.of(21, 0));

        assertThatThrownBy(() -> service.updateBatch(List.of(
                item(SettingKey.PREVIEW_TTL_MINUTES, "60"),
                item(SettingKey.EVENTS_HOURS_START, "22:00"))))
                .isInstanceOf(InvalidSettingValueException.class);

        verify(store, never()).write(any(), any());
    }

    @Test
    @DisplayName("lote con un valor inválido después de uno válido no escribe ninguna clave")
    void batchWithInvalidValueAfterValidOneWritesNothing() {
        assertThatThrownBy(() -> service.updateBatch(List.of(
                item(SettingKey.PREVIEW_TTL_MINUTES, "60"),
                item(SettingKey.PREVIEW_DEFAULT_TIME_LIMIT_SECONDS, "99999"))))
                .isInstanceOf(InvalidSettingValueException.class);

        verify(store, never()).write(any(), any());
    }

    @Test
    @DisplayName("lote válido escribe las claves en el orden recibido")
    void validBatchWritesInOrder() {
        service.updateBatch(List.of(
                item(SettingKey.PREVIEW_TTL_MINUTES, "60"),
                item(SettingKey.NOTIFICATIONS_ENABLED, "false")));

        InOrder order = inOrder(store);
        order.verify(store).write(SettingKey.PREVIEW_TTL_MINUTES, "60");
        order.verify(store).write(SettingKey.NOTIFICATIONS_ENABLED, "false");
    }

    @Test
    @DisplayName("fin 00:00 es válido con cualquier inicio vigente")
    void endAtMidnightIsAccepted() {
        when(store.getTime(SettingKey.EVENTS_HOURS_START)).thenReturn(LocalTime.of(8, 0));

        service.update(SettingKey.EVENTS_HOURS_END.getKey(), "00:00");

        verify(store).write(SettingKey.EVENTS_HOURS_END, "00:00");
    }

    @Test
    @DisplayName("fin 00:00 es válido también con inicio vigente 00:00")
    void endAtMidnightIsAcceptedWithStartAtMidnight() {
        when(store.getTime(SettingKey.EVENTS_HOURS_START)).thenReturn(LocalTime.MIDNIGHT);

        service.update(SettingKey.EVENTS_HOURS_END.getKey(), "00:00");

        verify(store).write(SettingKey.EVENTS_HOURS_END, "00:00");
    }

    @Test
    @DisplayName("inicio igual al fin (08:00/08:00) lanza y no escribe")
    void startEqualToEndThrows() {
        assertThatThrownBy(() -> service.updateBatch(List.of(
                item(SettingKey.EVENTS_HOURS_START, "08:00"),
                item(SettingKey.EVENTS_HOURS_END, "08:00"))))
                .isInstanceOf(InvalidSettingValueException.class)
                .hasMessage("La hora de inicio de la ventana horaria (08:00) debe ser anterior a la hora de fin (08:00)");

        verify(store, never()).write(any(), any());
    }

    @Test
    @DisplayName("una clave ajena a la ventana horaria no consulta store.getTime")
    void keyOutsideWindowDoesNotReadTimes() {
        SettingResponseDto result = service.update(SettingKey.PREVIEW_TTL_MINUTES.getKey(), "60");

        assertThat(result.value()).isEqualTo("60");
        verify(store).write(SettingKey.PREVIEW_TTL_MINUTES, "60");
        verify(store, never()).getTime(any());
    }
}
