package ar.edu.utn.frc.siga.common.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DurationMinutesConverter")
class DurationMinutesConverterTest {

    private final DurationMinutesConverter converter = new DurationMinutesConverter();

    @Test
    @DisplayName("convierte una Duration a minutos enteros para la columna de BD")
    void convertsDurationToMinutes() {
        Integer minutes = converter.convertToDatabaseColumn(Duration.ofMinutes(90));

        assertThat(minutes).isEqualTo(90);
    }

    @Test
    @DisplayName("convierte minutos de la columna de BD a Duration")
    void convertsMinutesToDuration() {
        Duration duration = converter.convertToEntityAttribute(90);

        assertThat(duration).isEqualTo(Duration.ofMinutes(90));
    }

    @Test
    @DisplayName("ida y vuelta Duration -> minutos -> Duration preserva el valor")
    void roundTripPreservesValue() {
        Duration original = Duration.ofMinutes(135);

        Integer minutes = converter.convertToDatabaseColumn(original);
        Duration restored = converter.convertToEntityAttribute(minutes);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    @DisplayName("redondea al minuto más cercano: segundos sueltos redondean half-up")
    void roundsToNearestMinute() {
        assertThat(converter.convertToDatabaseColumn(Duration.ofSeconds(125))).isEqualTo(2);
        assertThat(converter.convertToDatabaseColumn(Duration.ofSeconds(150))).isEqualTo(3);
        assertThat(converter.convertToDatabaseColumn(Duration.ofSeconds(59))).isEqualTo(1);
    }

    @Test
    @DisplayName("convertToDatabaseColumn es null-safe")
    void convertToDatabaseColumnIsNullSafe() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    @DisplayName("convertToEntityAttribute es null-safe")
    void convertToEntityAttributeIsNullSafe() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
