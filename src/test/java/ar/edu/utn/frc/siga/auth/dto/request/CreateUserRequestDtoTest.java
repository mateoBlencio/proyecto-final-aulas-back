package ar.edu.utn.frc.siga.auth.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("CreateUserRequestDto")
class CreateUserRequestDtoTest {

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

    /** Email válido para @Email de exactamente {@code length} caracteres: local de 64, dominio con dos etiquetas largas. */
    private static String emailOfLength(int length) {
        String local = "a".repeat(64);
        int domainLength = length - local.length() - 1;
        int firstLabel = (domainLength - ".com".length() - 1) / 2;
        int secondLabel = domainLength - ".com".length() - 1 - firstLabel;
        String email = local + "@" + "b".repeat(firstLabel) + "." + "c".repeat(secondLabel) + ".com";
        assertThat(email).hasSize(length);
        return email;
    }

    private static Set<ConstraintViolation<CreateUserRequestDto>> sizeViolations(CreateUserRequestDto dto) {
        return validator.validate(dto).stream()
                .filter(v -> v.getConstraintDescriptor().getAnnotation() instanceof Size)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static List<String> sizeViolatedFields(CreateUserRequestDto dto) {
        return sizeViolations(dto).stream().map(v -> v.getPropertyPath().toString()).sorted().toList();
    }

    private static CreateUserRequestDto withPassword(String password) {
        return new CreateUserRequestDto("ok@frc.utn.edu.ar", password, "Nombre", "Apellido", null);
    }

    @Test
    @DisplayName("un alta con valores típicos no tiene violaciones")
    void validDtoHasNoViolations() {
        assertThat(validator.validate(withPassword("clave-segura-2026"))).isEmpty();
    }

    @ParameterizedTest(name = "password de {0} caracteres -> violación de tamaño: {1}")
    @CsvSource({
            "72, false",
            "73, true"
    })
    @DisplayName("password: máximo 72 caracteres, con el valor límite y su vecino inválido")
    void passwordLengthBoundaries(int length, boolean violated) {
        List<String> fields = sizeViolatedFields(withPassword("p".repeat(length)));

        assertThat(fields).isEqualTo(violated ? List.of("password") : List.of());
    }

    @ParameterizedTest(name = "firstName y lastName de {0} caracteres -> violación de tamaño: {1}")
    @CsvSource({
            "100, false",
            "101, true"
    })
    @DisplayName("firstName y lastName: máximo 100 caracteres")
    void nameLengthBoundaries(int length, boolean violated) {
        String name = "n".repeat(length);
        CreateUserRequestDto dto = new CreateUserRequestDto("ok@frc.utn.edu.ar", "clave-segura-2026", name, name, null);

        assertThat(sizeViolatedFields(dto))
                .isEqualTo(violated ? List.of("firstName", "lastName") : List.of());
    }

    @ParameterizedTest(name = "email de {0} caracteres -> violación de tamaño: {1}")
    @CsvSource({
            "150, false",
            "151, true"
    })
    @DisplayName("email: máximo 150 caracteres")
    void emailLengthBoundaries(int length, boolean violated) {
        CreateUserRequestDto dto = new CreateUserRequestDto(
                emailOfLength(length), "clave-segura-2026", "Nombre", "Apellido", null);

        assertThat(sizeViolatedFields(dto)).isEqualTo(violated ? List.of("email") : List.of());
    }
}
