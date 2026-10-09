package ar.edu.utn.frc.siga.audit.dto.response;

/**
 * Change of one field in an audited record. {@code field} is the Java property name; relations show the
 * id of the related record. A null value means the field had no value before ({@code oldValue}) or has
 * none after ({@code newValue}).
 */
public record FieldChangeDto(String field, String oldValue, String newValue) {
}
