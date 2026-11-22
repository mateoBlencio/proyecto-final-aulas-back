package ar.edu.utn.frc.siga.auth.service.impl;

import ar.edu.utn.frc.siga.auth.exception.FieldValidationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

    private static final int MIN_CODE_POINTS = 12;
    private static final int MAX_BYTES = 72;
    private static final int MIN_PERSONAL_TOKEN_LENGTH = 4;
    private static final String COMMON_PASSWORDS = "security/common-passwords.txt";

    private final Set<String> commonPasswords = loadCommonPasswords();

    public void validate(String field, String password, String email, String firstName, String lastName) {
        List<String> errors = new ArrayList<>();
        if (password.codePointCount(0, password.length()) < MIN_CODE_POINTS) {
            errors.add("La contraseña debe tener al menos 12 caracteres.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            errors.add("La contraseña no puede superar los 72 bytes. Las tildes y los símbolos ocupan más de uno.");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (commonPasswords.contains(lower)) {
            errors.add("La contraseña es demasiado común.");
        }
        if (containsPersonalData(lower, email, firstName, lastName)) {
            errors.add("La contraseña no puede contener el nombre, el apellido ni el correo del usuario.");
        }
        if (password.codePoints().distinct().count() == 1) {
            errors.add("La contraseña no puede ser un solo carácter repetido.");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(field, errors);
        }
    }

    private static boolean containsPersonalData(String lowerPassword, String email, String firstName,
                                                String lastName) {
        return personalTokens(email, firstName, lastName).anyMatch(lowerPassword::contains);
    }

    private static Stream<String> personalTokens(String email, String firstName, String lastName) {
        String localPart = email == null ? "" : email.substring(0, email.indexOf('@') < 0 ? email.length() : email.indexOf('@'));
        return Stream.concat(Stream.of(localPart), Stream.concat(words(firstName), words(lastName)))
                .map(token -> token.toLowerCase(Locale.ROOT))
                .filter(token -> token.codePointCount(0, token.length()) >= MIN_PERSONAL_TOKEN_LENGTH);
    }

    private static Stream<String> words(String name) {
        return name == null ? Stream.empty() : Stream.of(name.strip().split("\s+"));
    }

    private static Set<String> loadCommonPasswords() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(COMMON_PASSWORDS).getInputStream(), StandardCharsets.UTF_8))) {
            Set<String> passwords = new HashSet<>();
            reader.lines().map(line -> line.toLowerCase(Locale.ROOT)).forEach(passwords::add);
            return Set.copyOf(passwords);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar " + COMMON_PASSWORDS, e);
        }
    }
}
