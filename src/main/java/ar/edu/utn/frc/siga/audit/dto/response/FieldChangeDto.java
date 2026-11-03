package ar.edu.utn.frc.siga.audit.dto.response;

/**
 * Change of one field in an audited record. {@code field} is the Java property name and {@code label} its name
 * in Spanish. The values are formatted for a person (references resolved to their label, enums translated,
 * {@code Sí}/{@code No}, dates as {@code dd/MM/yyyy}). A null value means the field had no value before
 * ({@code oldValue}) or has none after ({@code newValue}).
 */
public record FieldChangeDto(String field, String label, String oldValue, String newValue) {
}
