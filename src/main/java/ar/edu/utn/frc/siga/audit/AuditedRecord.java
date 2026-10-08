package ar.edu.utn.frc.siga.audit;

import org.springframework.modulith.NamedInterface;

import java.util.Map;

/**
 * Audited state of a record, as handed to an {@link AuditLabelProvider}. {@code values} are the audited
 * columns by Java property name (a to-one reference appears under its property name with the foreign key as value).
 */
@NamedInterface("api")
public record AuditedRecord(String recordId, Map<String, Object> values) {

    /** Numeric value of {@code property} (a number or a numeric string), or null if absent or unparseable. */
    public Long longValue(String property) {
        Object value = values.get(property);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value instanceof String text ? parseLong(text) : null;
    }

    /** Parses {@code text} as a long, or returns null if it is not numeric. */
    public static Long parseLong(String text) {
        try {
            return text == null ? null : Long.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Text value of {@code property}, or null if absent. */
    public String text(String property) {
        Object value = values.get(property);
        return value == null ? null : value.toString();
    }
}
