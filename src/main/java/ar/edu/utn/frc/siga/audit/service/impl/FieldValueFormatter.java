package ar.edu.utn.frc.siga.audit.service.impl;

import ar.edu.utn.frc.siga.audit.service.AuditedEntity.AuditedColumn;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Formats the audited values of a page for a person; references are replaced by their preloaded label. */
public final class FieldValueFormatter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public record ReferenceKey(Class<?> type, String id) {
    }

    private final Map<ReferenceKey, String> references;

    public FieldValueFormatter(Map<ReferenceKey, String> references) {
        this.references = references;
    }

    public static FieldValueFormatter withoutReferences() {
        return new FieldValueFormatter(Map.of());
    }

    String label(AuditedColumn column) {
        return FieldLabels.property(column.property());
    }

    String format(AuditedColumn column, Object value) {
        if (value == null) {
            return null;
        }
        if (column.targetType() != null) {
            String id = value.toString();
            return references.getOrDefault(new ReferenceKey(column.targetType(), id), id);
        }
        return switch (value) {
            case Boolean flag -> flag ? "Sí" : "No";
            case LocalDate date -> DATE.format(date);
            case LocalDateTime dateTime -> DATE_TIME.format(dateTime);
            case LocalTime time -> TIME.format(time);
            case String text when isEnum(column) -> FieldLabels.constant(text);
            default -> value.toString();
        };
    }

    private static boolean isEnum(AuditedColumn column) {
        return column.valueType() != null && column.valueType().isEnum();
    }
}
