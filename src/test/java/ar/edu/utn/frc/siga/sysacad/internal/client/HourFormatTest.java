package ar.edu.utn.frc.siga.sysacad.internal.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.format.DateTimeParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HourFormat: hora de la vista al entero del procedure")
class HourFormatTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"14:55,1455", "09:40,940", "00:05,5", "10:00,1000", "23:59,2359", "00:00,0"})
    @DisplayName("HH:mm pasa a HHmm sin ceros a la izquierda")
    void convertsToProcedureHour(String time, int expected) {
        assertThat(HourFormat.toProcedureHour(time)).isEqualTo(expected);
    }

    @Test
    @DisplayName("ignora espacios alrededor de la hora")
    void trimsInput() {
        assertThat(HourFormat.toProcedureHour(" 14:55 ")).isEqualTo(1455);
    }

    @Test
    @DisplayName("una hora inválida lanza DateTimeParseException")
    void rejectsInvalidTime() {
        assertThatThrownBy(() -> HourFormat.toProcedureHour("25:00")).isInstanceOf(DateTimeParseException.class);
    }
}
