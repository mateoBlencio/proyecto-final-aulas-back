package ar.edu.utn.frc.siga.audit;

import java.time.LocalDateTime;

public record RevisionMetadata(
        String recordId,
        Integer revision,
        LocalDateTime date,
        String user,
        RevisionKind kind,
        String description,
        String operationId) {
}
