package ar.edu.utn.frc.siga.audit.dto.response;

import ar.edu.utn.frc.siga.audit.model.ActorType;
import ar.edu.utn.frc.siga.audit.model.RevisionKind;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entrada del registro de auditoría. Según {@code type}:
 * <ul>
 *   <li>{@code CHANGE}: cambio individual. Trae {@code kind}, {@code entityType}, {@code recordId}.
 *       {@code operationId} es null salvo que el cambio pertenezca a una operación.</li>
 *   <li>{@code OPERATION}: operación de negocio en lote. Trae {@code operationId}, {@code recordCount}
 *       y {@code entityTypes}; el detalle se obtiene con {@code GET /v1/audit/operations/{operationId}}.
 *       {@code entityType} and {@code recordId} are null.</li>
 *   <li>{@code TRANSACTION}: transaction without an operation that touched more than one record. Carries {@code revision},
 *       {@code recordCount} and {@code entityTypes}; the detail is fetched with
 *       {@code GET /v1/audit/revisions/{revision}}. {@code operationId}, {@code entityType} and
 *       {@code recordId} are null.</li>
 * </ul>
 * {@code actorType} indicates whether a person ({@code HUMAN}) or a system process ({@code SYSTEM}) made
 * the change; a null {@code user} with {@code HUMAN} is a request from the public form.
 * {@code changes} is the field-level diff of a {@code CHANGE}. It is only filled in the items of
 * {@code GET /v1/audit/operations/{operationId}} and {@code GET /v1/audit/revisions/{revision}}; it is null in the
 * main listing and in {@code OPERATION} and {@code TRANSACTION}. It is empty when no audited field changed.
 * In a drill-down, null means the audited state of the record is not available.
 * For {@code OPERATION} and {@code TRANSACTION}, {@code kind} is the change type shared by all their rows,
 * or null if they are mixed. When the {@code kind} filter is applied, it always matches the filter.
 */
public record AuditLogEntryDto(
        AuditLogEntryType type,
        Integer revision,
        LocalDateTime date,
        String user,
        ActorType actorType,
        String description,
        RevisionKind kind,
        String entityType,
        String recordId,
        String operationId,
        Integer recordCount,
        List<String> entityTypes,
        @Schema(description = "Diff por campo de un CHANGE. Null en el listado principal y en OPERATION/TRANSACTION. "
                + "Vacío si no cambió ningún campo auditado. En un drill-down, null si el estado auditado "
                + "del registro no está disponible.")
        List<FieldChangeDto> changes) {
}
