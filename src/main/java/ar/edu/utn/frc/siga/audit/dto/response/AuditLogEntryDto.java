package ar.edu.utn.frc.siga.audit.dto.response;

import ar.edu.utn.frc.siga.audit.RevisionKind;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entrada del registro de auditoría. Según {@code type}:
 * <ul>
 *   <li>{@code CHANGE}: cambio individual. Trae {@code kind}, {@code entityType}, {@code recordId}.
 *       {@code operationId} es null salvo que el cambio pertenezca a una operación.</li>
 *   <li>{@code OPERATION}: operación de negocio en lote. Trae {@code operationId}, {@code recordCount}
 *       y {@code entityTypes}; el detalle se obtiene con {@code GET /v1/audit/operations/{operationId}}.
 *       {@code kind}, {@code entityType} y {@code recordId} son null.</li>
 * </ul>
 */
public record AuditLogEntryDto(
        AuditLogEntryType type,
        Integer revision,
        LocalDateTime date,
        String user,
        String description,
        RevisionKind kind,
        String entityType,
        String recordId,
        String operationId,
        Integer recordCount,
        List<String> entityTypes) {
}
