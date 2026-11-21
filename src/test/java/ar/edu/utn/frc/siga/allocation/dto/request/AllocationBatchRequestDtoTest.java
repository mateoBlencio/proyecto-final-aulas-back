package ar.edu.utn.frc.siga.allocation.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Largo máximo de {@code observation}: la columna {@code asignacion_aula.observaciones} es varchar(255). */
@DisplayName("AllocationBatchRequestDto")
class AllocationBatchRequestDtoTest {

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

    private static List<String> observationViolations(String observation) {
        var items = List.of(new AllocationItemRequestDto(List.of(10L), null, null, null, 5L));
        return validator.validate(new AllocationBatchRequestDto(items, observation)).stream()
                .filter(v -> v.getPropertyPath().toString().equals("observation"))
                .map(ConstraintViolation::getMessage)
                .toList();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 254, 255})
    @DisplayName("observation de hasta 255 caracteres es válida")
    void observationUpTo255IsValid(int length) {
        assertThat(observationViolations("a".repeat(length))).isEmpty();
    }

    @Test
    @DisplayName("observation nula es válida")
    void nullObservationIsValid() {
        assertThat(observationViolations(null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {256, 500})
    @DisplayName("observation de más de 255 caracteres se rechaza")
    void observationOver255IsRejected(int length) {
        assertThat(observationViolations("a".repeat(length))).hasSize(1);
    }
}
