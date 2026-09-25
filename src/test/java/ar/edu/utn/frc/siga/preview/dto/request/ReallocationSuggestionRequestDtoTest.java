package ar.edu.utn.frc.siga.preview.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Reglas {@code @AssertTrue} de {@link ReallocationSuggestionRequestDto} (target y rango). */
@DisplayName("ReallocationSuggestionRequestDto")
class ReallocationSuggestionRequestDtoTest {

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

    @Test
    @DisplayName("eventId sin from: rechazado")
    void eventIdSinFrom() {
        ReallocationSuggestionRequestDto dto =
                new ReallocationSuggestionRequestDto(null, 42L, null, null, null);

        assertThat(violations(dto)).isNotEmpty();
    }

    @Test
    @DisplayName("occurrenceIds y eventId juntos: rechazado")
    void ambosTargets() {
        ReallocationSuggestionRequestDto dto = new ReallocationSuggestionRequestDto(
                List.of(1L), 42L, LocalDate.now().plusDays(1), null, null);

        assertThat(violations(dto)).isNotEmpty();
    }

    @Test
    @DisplayName("ni occurrenceIds ni eventId: rechazado")
    void ningunTarget() {
        ReallocationSuggestionRequestDto dto =
                new ReallocationSuggestionRequestDto(null, null, null, null, null);

        assertThat(violations(dto)).isNotEmpty();
    }

    @Test
    @DisplayName("to sin from (sin eventId): rechazado")
    void toSinFrom() {
        ReallocationSuggestionRequestDto dto = new ReallocationSuggestionRequestDto(
                List.of(1L), null, null, LocalDate.now().plusDays(10), null);

        assertThat(violations(dto)).isNotEmpty();
    }

    @Test
    @DisplayName("occurrenceIds solo: válido")
    void occurrenceIdsSolo() {
        ReallocationSuggestionRequestDto dto =
                new ReallocationSuggestionRequestDto(List.of(1L), null, null, null, null);

        assertThat(violations(dto)).isEmpty();
    }

    @Test
    @DisplayName("eventId + from: válido")
    void eventIdMasFrom() {
        ReallocationSuggestionRequestDto dto = new ReallocationSuggestionRequestDto(
                null, 42L, LocalDate.now().plusDays(1), null, null);

        assertThat(violations(dto)).isEmpty();
    }

    private Set<ConstraintViolation<ReallocationSuggestionRequestDto>> violations(ReallocationSuggestionRequestDto dto) {
        return validator.validate(dto);
    }
}
