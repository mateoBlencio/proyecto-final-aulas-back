package ar.edu.utn.frc.siga.events.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CreateRecurringEventRequestDto")
class CreateRecurringEventRequestDtoTest {

    private static final String END_BEFORE_START = "La fecha de fin no puede ser anterior a la de inicio";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        factory.close();
    }

    private static List<String> messages(LocalDate startDate, LocalDate endDate) {
        var dto = new CreateRecurringEventRequestDto(30, LocalTime.of(8, 0), 90, DayOfWeek.MONDAY,
                startDate, endDate, 1L, 1L);
        return validator.validate(dto).stream().map(ConstraintViolation::getMessage).toList();
    }

    @Test
    @DisplayName("fecha de fin un día antes de la de inicio → falla con el mensaje de rango")
    void endDateAntesDeStartDateFalla() {
        assertThat(messages(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 9))).containsExactly(END_BEFORE_START);
    }

    @Test
    @DisplayName("fecha de fin igual a la de inicio (borde) → pasa")
    void endDateIgualAStartDatePasa() {
        assertThat(messages(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 10))).isEmpty();
    }

    @Test
    @DisplayName("fecha de fin posterior a la de inicio → pasa")
    void endDatePosteriorPasa() {
        assertThat(messages(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 11))).isEmpty();
    }

    @Test
    @DisplayName("fecha de fin nula (evento sin fin) → pasa")
    void endDateNulaPasa() {
        assertThat(messages(LocalDate.of(2026, 3, 10), null)).isEmpty();
    }

    @Test
    @DisplayName("fecha de inicio nula con fin informado → solo falla @NotNull de startDate, no la regla de rango")
    void startDateNulaNoDisparaLaReglaDeRango() {
        assertThat(messages(null, LocalDate.of(2026, 3, 10))).doesNotContain(END_BEFORE_START);
    }
}
