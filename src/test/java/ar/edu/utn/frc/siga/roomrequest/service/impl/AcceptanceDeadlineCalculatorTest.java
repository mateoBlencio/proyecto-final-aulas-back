package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.HolidayResponseDto;
import ar.edu.utn.frc.siga.academic.service.HolidayService;
import ar.edu.utn.frc.siga.settings.api.SettingsReader;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AcceptanceDeadlineCalculator")
class AcceptanceDeadlineCalculatorTest {

    @Mock
    private HolidayService holidayService;
    @Mock
    private SettingsReader settingsReader;
    @InjectMocks
    private AcceptanceDeadlineCalculator calculator;

    @BeforeEach
    void defaults() {
        lenient().when(settingsReader.getInt(SettingKey.ROOM_REQUESTS_ACCEPTANCE_BUSINESS_HOURS)).thenReturn(48);
        lenient().when(settingsReader.getInt(SettingKey.ROOM_REQUESTS_ACCEPTANCE_HOURS_BEFORE_EVENT)).thenReturn(24);
        lenient().when(holidayService.findBetween(any(), any())).thenReturn(List.of());
    }

    private static Instant at(int day, int hour) {
        return LocalDateTime.of(2026, 11, day, hour, 0).atZone(AcceptanceDeadlineCalculator.ZONE).toInstant();
    }

    private static LocalDateTime local(int day, int hour) {
        return LocalDateTime.of(2026, 11, day, hour, 0);
    }

    @Test
    @DisplayName("sin evento: el fin de semana no cuenta, jueves 10:00 + 48 h hábiles es lunes 10:00")
    void finDeSemanaNoCuenta() {
        assertThat(calculator.calculate(at(12, 10), null)).isEqualTo(at(16, 10));
    }

    @Test
    @DisplayName("ejemplo 1: parcial del sábado 12:00 notificado el jueves 10:00, gana el margen: viernes 12:00")
    void eventoAntesDelPlazo() {
        assertThat(calculator.calculate(at(12, 10), local(14, 12))).isEqualTo(at(13, 12));
    }

    @Test
    @DisplayName("notificado con tiempo (lunes 02/11) para el mismo parcial: el margen no cambia nada, miércoles 10:00")
    void notificadoConTiempo() {
        assertThat(calculator.calculate(at(2, 10), local(14, 12))).isEqualTo(at(4, 10));
    }

    @Test
    @DisplayName("ejemplo 2: cambio regular, viernes 06/11 10:00, clase el martes 10/11 18:00: gana el lunes 18:00")
    void cambioRegularGanaElMargen() {
        assertThat(calculator.calculate(at(6, 10), local(10, 18))).isEqualTo(at(9, 18));
    }

    @Test
    @DisplayName("con feriado el lunes 09/11 las 48 h hábiles terminan el miércoles 11/11 10:00")
    void feriadoCorreElPlazo() {
        when(holidayService.findBetween(any(), any()))
                .thenReturn(List.of(new HolidayResponseDto(LocalDate.of(2026, 11, 9), "Feriado")));

        assertThat(calculator.calculate(at(6, 10), null)).isEqualTo(at(11, 10));
    }

    @Test
    @DisplayName("con feriado, el margen sigue ganando si la clase es antes: lunes 18:00")
    void feriadoYMargen() {
        when(holidayService.findBetween(any(), any()))
                .thenReturn(List.of(new HolidayResponseDto(LocalDate.of(2026, 11, 9), "Feriado")));

        assertThat(calculator.calculate(at(6, 10), local(10, 18))).isEqualTo(at(9, 18));
    }

    @Test
    @DisplayName("ejemplo 6: notificado después del margen, el plazo queda en el pasado")
    void notificadoDespuesDelMargen() {
        Instant deadline = calculator.calculate(at(13, 20), local(14, 12));

        assertThat(deadline).isEqualTo(at(13, 12));
        assertThat(deadline).isBefore(at(13, 20));
    }

    @Test
    @DisplayName("notificado un sábado, las 48 h hábiles cuentan desde el lunes: martes a la misma hora")
    void notificadoUnSabado() {
        assertThat(calculator.calculate(at(7, 10), null)).isEqualTo(at(10, 10));
    }

    @Test
    @DisplayName("calcula en hora argentina: las 23:00 del jueves local son las 02:00Z del viernes, y el plazo sigue siendo lunes 23:00 local")
    void horaLocalNoUtc() {
        Instant lateThursday = ZonedDateTime.of(2026, 11, 12, 23, 0, 0, 0, AcceptanceDeadlineCalculator.ZONE).toInstant();

        assertThat(calculator.calculate(lateThursday, null)).isEqualTo(at(16, 23));
    }

    @Test
    @DisplayName("el margen se lee del setting: con 0 h el evento no adelanta nada")
    void margenCero() {
        when(settingsReader.getInt(SettingKey.ROOM_REQUESTS_ACCEPTANCE_HOURS_BEFORE_EVENT)).thenReturn(0);

        assertThat(calculator.calculate(at(12, 10), local(14, 12))).isEqualTo(at(14, 12));
    }

    @Test
    @DisplayName("las horas que no completan un día hábil se suman corridas: 60 h son dos días hábiles más 12 h")
    void horasSueltas() {
        when(settingsReader.getInt(SettingKey.ROOM_REQUESTS_ACCEPTANCE_BUSINESS_HOURS)).thenReturn(60);

        assertThat(calculator.calculate(at(12, 10), null)).isEqualTo(at(16, 22));
    }
}
