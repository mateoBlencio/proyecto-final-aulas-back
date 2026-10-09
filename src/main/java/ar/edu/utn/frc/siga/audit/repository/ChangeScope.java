package ar.edu.utn.frc.siga.audit.repository;

import ar.edu.utn.frc.siga.audit.service.AuditedEntity;
import ar.edu.utn.frc.siga.common.exception.InvalidSelectionException;

/**
 * Scopes a drill-down to an operation, an Envers revision or the history of one record.
 * Exactly one of {@code operationId}, {@code revision} or {@code entity}/{@code recordId} is set (enforced).
 * A record scope matches only the criteria target equal to {@code entity}.
 */
public record ChangeScope(String operationId, Integer revision, AuditedEntity entity, Object recordId) {

    public ChangeScope {
        boolean byOperation = operationId != null && revision == null && entity == null && recordId == null;
        boolean byRevision = operationId == null && revision != null && entity == null && recordId == null;
        boolean byRecord = operationId == null && revision == null && entity != null && recordId != null;
        if (!(byOperation || byRevision || byRecord)) {
            throw new IllegalArgumentException(
                    "ChangeScope requires exactly one of: operationId, revision, or entity with recordId");
        }
    }

    public static ChangeScope ofOperation(String operationId) {
        return new ChangeScope(operationId, null, null, null);
    }

    public static ChangeScope ofRevision(int revision) {
        return new ChangeScope(null, revision, null, null);
    }

    /** {@code recordId} is converted to the id type of {@code entity}; 400 if it cannot be converted. */
    public static ChangeScope ofRecord(AuditedEntity entity, String recordId) {
        try {
            return new ChangeScope(null, null, entity, entity.convertId(recordId));
        } catch (IllegalArgumentException e) {
            throw new InvalidSelectionException("Identificador inválido para '" + entity.label() + "': '" + recordId + "'");
        }
    }
}
