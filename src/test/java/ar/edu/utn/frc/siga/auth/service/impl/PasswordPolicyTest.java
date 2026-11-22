package ar.edu.utn.frc.siga.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.utn.frc.siga.auth.exception.FieldValidationException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PasswordPolicy")
class PasswordPolicyTest {

    private static final String EMAIL = "jperez@frc.utn.edu.ar";
    private static final String FIRST_NAME = "Juan";
    private static final String LAST_NAME = "Gonzalez";
    private static final String PERSONAL_DATA_MESSAGE =
            "La contraseña no puede contener el nombre, el apellido ni el correo del usuario.";

    private final PasswordPolicy policy = new PasswordPolicy();

    private void validate(String password) {
        policy.validate("password", password, EMAIL, FIRST_NAME, LAST_NAME);
    }

    private static String twoByteChars(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new String(Character.toChars(0xC0 + i)))
                .collect(Collectors.joining());
    }

    private FieldValidationException failureOf(String password) {
        try {
            validate(password);
        } catch (FieldValidationException e) {
            return e;
        }
        throw new AssertionError("Se esperaba FieldValidationException para: " + password);
    }

    @SuppressWarnings("unchecked")
    private static List<String> passwordErrors(FieldValidationException e) {
        Map<String, List<String>> errors = (Map<String, List<String>>) e.getProperties().get("errors");
        assertThat(errors).containsOnlyKeys("password");
        return errors.get("password");
    }

    private List<String> errorsOf(String password) {
        return passwordErrors(failureOf(password));
    }

    @Test
    @DisplayName("11 code points falla por largo")
    void elevenCodePointsFails() {
        assertThat(errorsOf("clave-corta")).containsExactly("La contraseña debe tener al menos 12 caracteres.");
    }

    @Test
    @DisplayName("12 code points pasa")
    void twelveCodePointsPasses() {
        assertThatCode(() -> validate("clave-larga1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("6 emojis (12 unidades UTF-16, 6 code points) falla por largo")
    void sixEmojisFails() {
        String emojis = "😀😁😂😃😄😅";
        assertThat(emojis.length()).isEqualTo(12);

        assertThat(errorsOf(emojis)).containsExactly("La contraseña debe tener al menos 12 caracteres.");
    }

    @Test
    @DisplayName("36 caracteres distintos de 2 bytes (72 bytes) pasa")
    void exactly72BytesPasses() {
        assertThatCode(() -> validate(twoByteChars(36))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("37 caracteres distintos de 2 bytes (74 bytes) falla por bytes")
    void over72BytesFails() {
        assertThat(errorsOf(twoByteChars(37))).hasSize(1).first().asString().contains("72 bytes");
    }

    @Test
    @DisplayName("contraseña común en mayúsculas falla")
    void commonPasswordUppercaseFails() {
        assertThat(errorsOf("ILOVEYOUSOMUCH")).containsExactly("La contraseña es demasiado común.");
    }

    @Test
    @DisplayName("contiene la parte local del correo falla")
    void containingEmailLocalPartFails() {
        assertThat(errorsOf("xx-JPerez-clave-99")).containsExactly(PERSONAL_DATA_MESSAGE);
    }

    @Test
    @DisplayName("contiene el apellido falla")
    void containingLastNameFails() {
        assertThat(errorsOf("mi-gonzalez-clave-99")).containsExactly(PERSONAL_DATA_MESSAGE);
    }

    @Test
    @DisplayName("nombre de 3 letras contenido pasa")
    void shortFirstNameIsIgnored() {
        assertThatCode(() -> policy.validate("password", "xx-ana-clave-segura-99", "x@frc.utn.edu.ar", "Ana", "Perez"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un solo carácter repetido 20 veces falla")
    void repeatedSingleCharacterFails() {
        assertThat(errorsOf("a".repeat(20)))
                .contains("La contraseña no puede ser un solo carácter repetido.");
    }

    @Test
    @DisplayName("frase larga sin símbolos pasa")
    void longPassphrasePasses() {
        assertThatCode(() -> validate("el caballo azul corre por la montana")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("dos reglas rotas devuelven dos mensajes en el mismo campo")
    void twoBrokenRulesReturnTwoMessages() {
        FieldValidationException e = failureOf("jperez");

        assertThat(e.getProperties()).containsOnlyKeys("errors");
        assertThat(passwordErrors(e)).containsExactly(
                "La contraseña debe tener al menos 12 caracteres.", PERSONAL_DATA_MESSAGE);
    }

    @Test
    @DisplayName("usa el nombre de campo recibido")
    void usesGivenFieldName() {
        try {
            policy.validate("newPassword", "corta", EMAIL, FIRST_NAME, LAST_NAME);
        } catch (FieldValidationException e) {
            assertThat((Map<Object, Object>) e.getProperties().get("errors")).containsOnlyKeys("newPassword");
            return;
        }
        throw new AssertionError("Se esperaba FieldValidationException");
    }

    private void validateFor(String password, String firstName, String lastName) {
        policy.validate("password", password, "x@frc.utn.edu.ar", firstName, lastName);
    }

    @Test
    @DisplayName("apellido compuesto con guion: detecta cada parte sin tildes")
    void hyphenatedLastNameIsSplitIntoParts() {
        assertThatThrownBy(() -> validateFor("mi-garcia-clave-99", "Ana", "Pérez-García"))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(passwordErrors(e)).containsExactly(PERSONAL_DATA_MESSAGE));
        assertThatThrownBy(() -> validateFor("mi-perez-clave-99", "Ana", "Pérez-García"))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(passwordErrors(e)).containsExactly(PERSONAL_DATA_MESSAGE));
    }

    @Test
    @DisplayName("nombre con guion y con espacios: detecta cada parte")
    void hyphenatedAndSpacedFirstNameIsSplitIntoParts() {
        assertThatThrownBy(() -> validateFor("mi-lucia-clave-99", "María-Lucía Ana", "Gómez"))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(passwordErrors(e)).containsExactly(PERSONAL_DATA_MESSAGE));
        assertThatThrownBy(() -> validateFor("mi-maria-clave-99", "María-Lucía Ana", "Gómez"))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(passwordErrors(e)).containsExactly(PERSONAL_DATA_MESSAGE));
    }

    @Test
    @DisplayName("contraseña con tildes y mayúsculas contiene el apellido sin tildes")
    void accentedUppercasePasswordMatchesUnaccentedName() {
        assertThatThrownBy(() -> validateFor("xx-PÉREZ-clave-99", "Ana", "Perez"))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(passwordErrors(e)).containsExactly(PERSONAL_DATA_MESSAGE));
        assertThatThrownBy(() -> validateFor("xx-garcía-clave-99", "Ana", "Garcia"))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(passwordErrors(e)).containsExactly(PERSONAL_DATA_MESSAGE));
    }

    @Test
    @DisplayName("apellido con tildes y contraseña sin tildes también coinciden")
    void accentedNameMatchesUnaccentedPassword() {
        assertThatThrownBy(() -> validateFor("xx-gonzalez-clave-99", "Ana", "González"))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(passwordErrors(e)).containsExactly(PERSONAL_DATA_MESSAGE));
    }

    @Test
    @DisplayName("parte local del correo con tildes en la contraseña coincide")
    void emailLocalPartIsFoldedToo() {
        assertThatThrownBy(() -> policy.validate("password", "xx-JOSÉMARÍA-clave-99", "josemaria@frc.utn.edu.ar",
                "Ana", "Ruiz"))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(passwordErrors(e)).containsExactly(PERSONAL_DATA_MESSAGE));
    }

    @Test
    @DisplayName("partes de menos de 4 letras de un nombre compuesto no disparan")
    void shortPartsOfACompositeNameDoNotTrigger() {
        assertThatCode(() -> validateFor("xx-del-clave-segura-99", "Ana", "Del-Río"))
                .doesNotThrowAnyException();
        assertThatCode(() -> validateFor("xx-ana-clave-segura-99", "Ána", "Ruiz")).doesNotThrowAnyException();
    }
}
