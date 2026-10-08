package ar.edu.utn.frc.siga.audit.repository;

import ar.edu.utn.frc.siga.audit.model.ActorType;
import ar.edu.utn.frc.siga.audit.model.RevisionKind;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Log group: an operation (all its revisions) or a standalone revision. {@code commonKind}
 * is the kind shared by all rows of the group, or null if mixed. {@code singleEntityType} and
 * {@code singleRecordId} are only meaningful when {@code recordCount == 1}.
 */
public record AuditGroupRow(
        String operationId,
        int revision,
        LocalDateTime date,
        String user,
        ActorType actorType,
        String description,
        long recordCount,
        List<String> entityTypes,
        RevisionKind commonKind,
        String singleEntityType,
        String singleRecordId) {
}
