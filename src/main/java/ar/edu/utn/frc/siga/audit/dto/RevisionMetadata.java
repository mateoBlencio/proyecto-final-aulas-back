package ar.edu.utn.frc.siga.audit.dto;

import java.time.LocalDateTime;

import ar.edu.utn.frc.siga.audit.model.RevisionKind;

public record RevisionMetadata(
        String recordId,
        Integer revision,
        LocalDateTime date,
        String user,
        RevisionKind kind,
        String description,
        String operationId) {
}
