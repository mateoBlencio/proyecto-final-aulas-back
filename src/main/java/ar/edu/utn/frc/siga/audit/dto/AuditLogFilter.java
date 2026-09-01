package ar.edu.utn.frc.siga.audit.dto;

import ar.edu.utn.frc.siga.audit.RevisionKind;

import java.time.LocalDate;

public record AuditLogFilter(
        LocalDate from,
        LocalDate to,
        String user,
        String entityType,
        RevisionKind kind) {
}
