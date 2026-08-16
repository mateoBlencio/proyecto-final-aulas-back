package ar.edu.utn.frc.siga.settings.validator;

import ar.edu.utn.frc.siga.settings.exception.InvalidSettingValueException;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;

@Component
public class SettingValueValidator {

    public String validate(SettingKey key, String rawValue) {
        if (rawValue == null) {
            throw new InvalidSettingValueException("El valor de '" + key.getKey() + "' no puede ser nulo");
        }
        String value = rawValue.trim();
        return switch (key.getType()) {
            case INT -> String.valueOf(validateInt(key, value));
            case LONG -> String.valueOf(validateLong(key, value));
            case BOOLEAN -> validateBoolean(key, value);
            case TIME -> validateTime(key, value).toString();
        };
    }

    private int validateInt(SettingKey key, String value) {
        int parsed = parse(key, value, "un entero", () -> Integer.parseInt(value));
        checkBounds(key, parsed,
                key.getMin() == null ? null : Long.parseLong(key.getMin()),
                key.getMax() == null ? null : Long.parseLong(key.getMax()));
        return parsed;
    }

    private long validateLong(SettingKey key, String value) {
        long parsed = parse(key, value, "un entero largo", () -> Long.parseLong(value));
        checkBounds(key, parsed,
                key.getMin() == null ? null : Long.parseLong(key.getMin()),
                key.getMax() == null ? null : Long.parseLong(key.getMax()));
        return parsed;
    }

    private String validateBoolean(SettingKey key, String value) {
        if (value.equalsIgnoreCase("true")) return "true";
        if (value.equalsIgnoreCase("false")) return "false";
        throw new InvalidSettingValueException(
                "El valor de '" + key.getKey() + "' debe ser 'true' o 'false', pero fue: " + value);
    }

    private LocalTime validateTime(SettingKey key, String value) {
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException ex) {
            throw new InvalidSettingValueException(
                    "El valor de '" + key.getKey() + "' debe ser una hora válida (HH:mm), pero fue: " + value);
        }
    }

    private <T> T parse(SettingKey key, String value, String expected, NumberParser<T> parser) {
        try {
            return parser.parse();
        } catch (NumberFormatException ex) {
            throw new InvalidSettingValueException(
                    "El valor de '" + key.getKey() + "' debe ser " + expected + ", pero fue: " + value);
        }
    }

    private void checkBounds(SettingKey key, long value, Long min, Long max) {
        if (min != null && value < min) {
            throw new InvalidSettingValueException(
                    "El valor de '" + key.getKey() + "' (" + value + ") es menor que el mínimo permitido (" + min + ")");
        }
        if (max != null && value > max) {
            throw new InvalidSettingValueException(
                    "El valor de '" + key.getKey() + "' (" + value + ") es mayor que el máximo permitido (" + max + ")");
        }
    }

    @FunctionalInterface
    private interface NumberParser<T> {
        T parse();
    }
}
