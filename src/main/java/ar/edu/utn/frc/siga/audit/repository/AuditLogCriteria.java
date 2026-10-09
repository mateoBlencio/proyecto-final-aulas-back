package ar.edu.utn.frc.siga.audit.repository;

import ar.edu.utn.frc.siga.audit.model.ActorType;
import ar.edu.utn.frc.siga.audit.model.RevisionKind;
import ar.edu.utn.frc.siga.audit.service.AuditedEntity;

import java.time.LocalDateTime;
import java.util.List;

/** Already-validated log filters. Null {@code from}/{@code toExclusive}/{@code user}/{@code kind}/{@code actor}/{@code q} do not filter. */
public record AuditLogCriteria(
        LocalDateTime from,
        LocalDateTime toExclusive,
        String user,
        RevisionKind kind,
        List<AuditedEntity> targets,
        ActorType actor,
        String q) {
}
