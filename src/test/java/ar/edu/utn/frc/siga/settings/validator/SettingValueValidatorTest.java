package ar.edu.utn.frc.siga.settings.validator;

import ar.edu.utn.frc.siga.settings.SettingsCatalogFixture;
import ar.edu.utn.frc.siga.settings.exception.InvalidSettingValueException;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SettingValueValidator")
class SettingValueValidatorTest {

    private final SettingValueValidator validator = new SettingValueValidator(SettingsCatalogFixture.catalog());

    @Test
    @DisplayName("INT dentro de cotas devuelve el valor canónico")
    void intWithinBoundsIsAccepted() {
        String result = validator.validate(SettingKey.OPTIMIZER_WEIGHT_OVERCROWDING, "5000");
        assertThat(result).isEqualTo("5000");
    }

    @Test
    @DisplayName("INT en los límites exactos (min y max) se acepta")
    void intAtBoundsIsAccepted() {
        assertThat(validator.validate(SettingKey.OPTIMIZER_WEIGHT_OVERCROWDING, "0")).isEqualTo("0");
        assertThat(validator.validate(SettingKey.OPTIMIZER_WEIGHT_OVERCROWDING, "1000000")).isEqualTo("1000000");
    }

    @Test
    @DisplayName("INT por debajo del mínimo lanza 400")
    void intBelowMinIsRejected() {
        assertThatThrownBy(() -> validator.validate(SettingKey.OPTIMIZER_SOLVER_SECONDS_SPENT_LIMIT, "0"))
                .isInstanceOf(InvalidSettingValueException.class);
    }

    @Test
    @DisplayName("INT por encima del máximo lanza 400")
    void intAboveMaxIsRejected() {
        assertThatThrownBy(() -> validator.validate(SettingKey.OPTIMIZER_WEIGHT_OVERCROWDING, "1000001"))
                .isInstanceOf(InvalidSettingValueException.class);
    }

    @Test
    @DisplayName("INT no numérico lanza 400")
    void intNonNumericIsRejected() {
        assertThatThrownBy(() -> validator.validate(SettingKey.OPTIMIZER_WEIGHT_OVERCROWDING, "abc"))
                .isInstanceOf(InvalidSettingValueException.class);
    }

    @Test
    @DisplayName("LONG dentro de cotas se acepta y fuera de cotas se rechaza")
    void longBounds() {
        assertThat(validator.validate(SettingKey.OPTIMIZER_UNIMPROVED_SECONDS_LIMIT, "3600")).isEqualTo("3600");
        assertThatThrownBy(() -> validator.validate(SettingKey.OPTIMIZER_UNIMPROVED_SECONDS_LIMIT, "3601"))
                .isInstanceOf(InvalidSettingValueException.class);
        assertThatThrownBy(() -> validator.validate(SettingKey.OPTIMIZER_UNIMPROVED_SECONDS_LIMIT, "-1"))
                .isInstanceOf(InvalidSettingValueException.class);
    }

    @Test
    @DisplayName("TIME válido se acepta y se normaliza a HH:mm")
    void timeValidIsAccepted() {
        assertThat(validator.validate(SettingKey.EVENTS_HOURS_START, "08:00")).isEqualTo("08:00");
    }

    @Test
    @DisplayName("TIME con formato inválido lanza 400")
    void timeInvalidIsRejected() {
        assertThatThrownBy(() -> validator.validate(SettingKey.EVENTS_HOURS_END, "25:00"))
                .isInstanceOf(InvalidSettingValueException.class);
        assertThatThrownBy(() -> validator.validate(SettingKey.EVENTS_HOURS_END, "no-es-hora"))
                .isInstanceOf(InvalidSettingValueException.class);
    }

    @Test
    @DisplayName("Valor con espacios alrededor se normaliza")
    void trimmingIsApplied() {
        assertThatCode(() -> validator.validate(SettingKey.OPTIMIZER_WEIGHT_UNUSED_CAPACITY, "  42  "))
                .doesNotThrowAnyException();
        assertThat(validator.validate(SettingKey.OPTIMIZER_WEIGHT_UNUSED_CAPACITY, "  42  ")).isEqualTo("42");
    }
}
