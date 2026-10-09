package ar.edu.utn.frc.siga.audit.repository;

import ar.edu.utn.frc.siga.audit.dto.RevisionMetadata;
import ar.edu.utn.frc.siga.audit.service.AuditedEntity;

public record AuditChangeRow(RevisionMetadata metadata, AuditedEntity entity) {
}
