package ar.edu.utn.frc.siga.audit.repository;

import ar.edu.utn.frc.siga.audit.dto.RevisionMetadata;

public record AuditChangeRow(RevisionMetadata metadata, String entityType) {
}
